package com.glamardor.absolutecinema.render;

import com.glamardor.absolutecinema.AbsoluteCinema;
import com.glamardor.absolutecinema.CinemaManager;
import com.glamardor.absolutecinema.camera.CameraDirector;
import com.glamardor.absolutecinema.config.CinemaConfig;
import com.glamardor.absolutecinema.config.ColorGrade;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The picture half of the effect: depth of field, colour grade, vignette and grain, done as a
 * small hand-rolled post chain.
 *
 * <p>It does not go through vanilla's post effect json because those pipelines bake their
 * uniforms at load time, and every value here (focus distance above all) changes per frame.
 * The chain is: copy the frame aside, blur the copy at half resolution in two passes, then
 * composite copy + blur + depth back over the main framebuffer.
 */
public final class CinemaPostProcessor {
	private static final int UNIFORM_MAPPABLE = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE;
	private static final int CONFIG_BYTES = 9 * 16;
	private static final int SAMPLER_INFO_BYTES = 2 * 8;
	private static final float NEAR_PLANE = Camera.PROJECTION_Z_NEAR;

	/** Luminance above which a pixel is treated as a light source worth glowing. */
	private static final float BLOOM_THRESHOLD = 0.55f;

	@Nullable
	private static RenderPipeline blurPipeline;
	@Nullable
	private static RenderPipeline compositePipeline;

	@Nullable
	private static TextureTarget sceneCopy;
	@Nullable
	private static TextureTarget blurA;
	@Nullable
	private static TextureTarget blurB;
	@Nullable
	private static TextureTarget bloomA;
	@Nullable
	private static TextureTarget bloomB;

	@Nullable
	private static MappableRingBuffer configBuffer;
	@Nullable
	private static GpuBuffer samplerInfoBlurFirst;
	@Nullable
	private static GpuBuffer samplerInfoBlurSecond;
	@Nullable
	private static GpuBuffer samplerInfoComposite;
	@Nullable
	private static GpuBuffer blurDirHorizontal;
	@Nullable
	private static GpuBuffer blurDirVertical;
	@Nullable
	private static GpuBuffer bloomDirHorizontal;
	@Nullable
	private static GpuBuffer bloomDirVertical;

	private static int width;
	private static int height;
	private static boolean disabled;

	private static float focusDistance = 8.0f;
	private static float grainClock;

	/** Set with -Dabsolutecinema.debug=true to trace why the filters are or are not running. */
	private static final boolean DEBUG = Boolean.getBoolean("absolutecinema.debug");
	private static String lastDebugState = "";
	private static boolean loggedFirstPass;
	private static boolean loggedInvocation;

	private CinemaPostProcessor() {
	}

	private static void debugState(String state) {
		if (DEBUG && !state.equals(lastDebugState)) {
			lastDebugState = state;
			AbsoluteCinema.LOGGER.info("[filters] {}", state);
		}
	}

	public static void render(Minecraft client, RenderTarget main) {
		if (DEBUG && !loggedInvocation) {
			loggedInvocation = true;
			AbsoluteCinema.LOGGER.info("[filters] render hook reached, framebuffer {}x{}, depth={}",
					main.width, main.height, main.hasDepth());
		}
		if (disabled || client.level == null) {
			debugState(disabled ? "disabled after an error" : "no world");
			return;
		}

		float weight = CinemaManager.getTransition();
		if (weight <= 0.002f) {
			debugState("transition is zero");
			return;
		}

		CinemaConfig config = CinemaConfig.get();
		ColorGrade grade = config.colorGrade;
		// Each preset decides whether a soft background belongs in its look at all.
		boolean wantsDof = config.depthOfField && config.dofStrength > 0.002f
				&& grade.allowsDepthOfField();
		boolean wantsGrade = config.colorGrading && grade != ColorGrade.NONE
				&& config.gradeStrength > 0.002f;
		boolean wantsVignette = config.vignette && wantsGrade && grade.getVignette() > 0.0f;
		boolean wantsGrain = config.filmGrain && wantsGrade && grade.getGrain() > 0.0f;
		boolean wantsBloom = wantsGrade && grade.getBloom() > 0.002f;
		if (!wantsDof && !wantsGrade && !wantsVignette && !wantsGrain) {
			debugState("nothing enabled to draw");
			return;
		}

		try {
			ensureResources(main);
			updateFocus(client, config);
			grainClock += CinemaManager.getFrameDelta();
			writeConfig(client, config, weight, wantsDof, wantsGrade, wantsVignette, wantsGrain, wantsBloom);
			runPasses(main, wantsDof, wantsBloom);
			if (DEBUG && !loggedFirstPass) {
				loggedFirstPass = true;
				AbsoluteCinema.LOGGER.info(
						"[filters] first pass drawn at {}x{} (dof={}, grade={}, focus={})",
						width, height, wantsDof, wantsGrade ? grade.getId() : "off", focusDistance);
			}
			debugState("running");
		} catch (Throwable t) {
			AbsoluteCinema.LOGGER.error("Cinema post processing failed; image filters are now off", t);
			disabled = true;
			release();
		}
	}

	/** Called when the game reloads resources, so a changed shader is picked up on F3+T. */
	public static void reload() {
		release();
		disabled = false;
	}

	private static void ensureResources(RenderTarget main) {
		if (blurPipeline == null) {
			// Laid out the way vanilla's PostChain builds a post pass: one bind group with every
			// sampler and uniform block the shaders declare, drawn as a single screen triangle.
			blurPipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
					.withLocation(AbsoluteCinema.id("pipeline/cinema_blur"))
					.withVertexShader(AbsoluteCinema.id("post/cinema_blur"))
					.withFragmentShader(AbsoluteCinema.id("post/cinema_blur"))
					.withBindGroupLayout(BindGroupLayout.builder()
							.withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
							.withUniform("SamplerInfo", UniformType.UNIFORM_BUFFER)
							.withUniform("CinemaConfig", UniformType.UNIFORM_BUFFER)
							.withUniform("BlurDir", UniformType.UNIFORM_BUFFER)
							.build())
					.withColorTargetState(ColorTargetState.DEFAULT)
					.build();
			compositePipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
					.withLocation(AbsoluteCinema.id("pipeline/cinema_composite"))
					.withVertexShader(AbsoluteCinema.id("post/cinema_composite"))
					.withFragmentShader(AbsoluteCinema.id("post/cinema_composite"))
					.withBindGroupLayout(BindGroupLayout.builder()
							.withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
							.withUniform("BlurSampler", UniformType.COMBINED_IMAGE_SAMPLER)
							.withUniform("BloomSampler", UniformType.COMBINED_IMAGE_SAMPLER)
							.withUniform("DepthSampler", UniformType.COMBINED_IMAGE_SAMPLER)
							.withUniform("SamplerInfo", UniformType.UNIFORM_BUFFER)
							.withUniform("CinemaConfig", UniformType.UNIFORM_BUFFER)
							.build())
					.withColorTargetState(ColorTargetState.DEFAULT)
					.build();
		}
		if (configBuffer == null) {
			configBuffer = new MappableRingBuffer(() -> "cinema config", UNIFORM_MAPPABLE, CONFIG_BYTES);
			// z marks the bloom chain (its own radius), w asks for the highlight cut.
			blurDirHorizontal = createVec4("cinema blur dir h", 1.0f, 0.0f, 0.0f, 0.0f);
			blurDirVertical = createVec4("cinema blur dir v", 0.0f, 1.0f, 0.0f, 0.0f);
			bloomDirHorizontal = createVec4("cinema bloom dir h", 1.0f, 0.0f, 1.0f, 1.0f);
			bloomDirVertical = createVec4("cinema bloom dir v", 0.0f, 1.0f, 1.0f, 0.0f);
		}

		int targetWidth = main.width;
		int targetHeight = main.height;
		if (sceneCopy != null && targetWidth == width && targetHeight == height) {
			return;
		}

		width = targetWidth;
		height = targetHeight;
		int halfWidth = Math.max(1, width / 2);
		int halfHeight = Math.max(1, height / 2);

		closeFramebuffers();
		// Same colour format as the main target, or the copy into sceneCopy is refused.
		sceneCopy = new TextureTarget("cinema scene", width, height, GpuFormat.RGBA8_UNORM, null);
		blurA = new TextureTarget("cinema blur a", halfWidth, halfHeight, GpuFormat.RGBA8_UNORM, null);
		blurB = new TextureTarget("cinema blur b", halfWidth, halfHeight, GpuFormat.RGBA8_UNORM, null);
		bloomA = new TextureTarget("cinema bloom a", halfWidth, halfHeight, GpuFormat.RGBA8_UNORM, null);
		bloomB = new TextureTarget("cinema bloom b", halfWidth, halfHeight, GpuFormat.RGBA8_UNORM, null);

		closeSamplerInfo();
		samplerInfoBlurFirst = createSamplerInfo("cinema si 1", halfWidth, halfHeight, width, height);
		samplerInfoBlurSecond = createSamplerInfo("cinema si 2", halfWidth, halfHeight, halfWidth, halfHeight);
		samplerInfoComposite = createSamplerInfo("cinema si 3", width, height, width, height);
	}

	private static GpuBuffer createSamplerInfo(String name, int outWidth, int outHeight, int inWidth, int inHeight) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			Std140Builder builder = Std140Builder.onStack(stack, SAMPLER_INFO_BYTES);
			builder.putVec2(outWidth, outHeight);
			builder.putVec2(inWidth, inHeight);
			return RenderSystem.getDevice().createBuffer(() -> name, GpuBuffer.USAGE_UNIFORM, builder.get());
		}
	}

	private static GpuBuffer createVec4(String name, float x, float y, float z, float w) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			Std140Builder builder = Std140Builder.onStack(stack, 16);
			builder.putVec4(x, y, z, w);
			return RenderSystem.getDevice().createBuffer(() -> name, GpuBuffer.USAGE_UNIFORM, builder.get());
		}
	}

	/** Pull focus smoothly towards whatever the shot is actually about. */
	private static void updateFocus(Minecraft client, CinemaConfig config) {
		float target = config.dofFocusDistance;
		if (config.dofAutoFocus) {
			target = measureSubjectDistance(client, config);
		}
		float dt = CinemaManager.getFrameDelta();
		float step = 1.0f - (float) Math.exp(-dt / 0.35f);
		focusDistance = Mth.lerp(step, focusDistance, target);
	}

	private static float measureSubjectDistance(Minecraft client, CinemaConfig config) {
		Camera camera = client.gameRenderer.mainCamera();
		Vec3 origin = camera.position();

		if (CinemaManager.isDirecting()) {
			double toSubject = CameraDirector.get().getLook().distanceTo(origin);
			if (toSubject > 0.4) {
				return (float) toSubject;
			}
		}

		if (client.level == null) {
			return config.dofFocusDistance;
		}
		Vec3 direction = Vec3.directionFromRotation(camera.xRot(), camera.yRot());
		Vec3 end = origin.add(direction.scale(64.0));
		HitResult hit = client.level.clip(new ClipContext(origin, end,
				ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, client.player));
		if (hit.getType() == HitResult.Type.MISS) {
			return 32.0f;
		}
		return (float) Mth.clamp(hit.getLocation().distanceTo(origin), 0.6, 64.0);
	}

	private static void writeConfig(Minecraft client, CinemaConfig config, float weight,
			boolean wantsDof, boolean wantsGrade, boolean wantsVignette, boolean wantsGrain,
			boolean wantsBloom) {
		ColorGrade grade = config.colorGrade;
		float ease = weight * weight * (3.0f - 2.0f * weight);

		float[] lift = wantsGrade ? grade.getLift() : new float[] { 0.0f, 0.0f, 0.0f };
		float[] gamma = wantsGrade ? grade.getGamma() : new float[] { 1.0f, 1.0f, 1.0f };
		float[] gain = wantsGrade ? grade.getGain() : new float[] { 1.0f, 1.0f, 1.0f };
		float saturation = wantsGrade ? grade.getSaturation() : 1.0f;
		float contrast = wantsGrade ? grade.getContrast() : 1.0f;
		float vignette = wantsVignette ? grade.getVignette() : 0.0f;
		float grain = wantsGrain ? grade.getGrain() : 0.0f;
		float dof = wantsDof ? config.dofStrength : 0.0f;
		float bloom = wantsBloom ? grade.getBloom() : 0.0f;
		float aberration = wantsGrade ? grade.getAberration() : 0.0f;
		float flicker = wantsGrade ? grade.getFlicker() : 0.0f;
		float warp = wantsGrade ? grade.getWarp() : 0.0f;
		float doubleVision = wantsGrade ? grade.getDoubleVision() : 0.0f;
		// The two chains size their kernels independently: the depth of field follows its own
		// strength, the glow always wants a wide one or it reads as "that lamp is a bit brighter".
		float dofDrive = dof;
		float bloomDrive = 1.0f;
		float gradeStrength = wantsGrade ? config.gradeStrength * ease : ease;
		float far = client.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.depthFar;
		float aspect = height == 0 ? 1.0f : (float) width / height;

		try (GpuBufferSlice.MappedView view = configBuffer.currentBuffer().map(false, true)) {
			ByteBuffer data = view.data();
			Std140Builder builder = Std140Builder.intoBuffer(data);
			builder.putVec4(lift[0], lift[1], lift[2], 0.0f);
			builder.putVec4(gamma[0], gamma[1], gamma[2], 0.0f);
			builder.putVec4(gain[0], gain[1], gain[2], 0.0f);
			builder.putVec4(saturation, contrast, vignette, grain);
			builder.putVec4(focusDistance, config.dofFocusRange, dof * ease, grainClock);
			builder.putVec4(NEAR_PLANE, far, aspect, gradeStrength);
			builder.putVec4(config.dofBlurForeground ? config.dofForegroundAmount : 0.0f,
					dofDrive * ease, bloom * ease, aberration * ease);
			builder.putVec4(flicker * ease, BLOOM_THRESHOLD, bloomDrive, warp * ease);
			builder.putVec4(doubleVision * ease, 0.0f, 0.0f, 0.0f);
		}
	}

	private static void runPasses(RenderTarget main, boolean wantsDof, boolean wantsBloom) {
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		GpuBuffer config = configBuffer.currentBuffer();
		GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

		encoder.copyTextureToTexture(main.getColorTexture(), sceneCopy.getColorTexture(),
				0, 0, 0, 0, 0, width, height);

		if (wantsDof) {
			blit(encoder, blurPipeline, blurA, samplerInfoBlurFirst, config, pass -> {
				pass.setUniform("BlurDir", blurDirHorizontal);
				pass.setUniform("InSampler", sceneCopy.getColorTextureView(), linear);
			});
			blit(encoder, blurPipeline, blurB, samplerInfoBlurSecond, config, pass -> {
				pass.setUniform("BlurDir", blurDirVertical);
				pass.setUniform("InSampler", blurA.getColorTextureView(), linear);
			});
		}

		if (wantsBloom) {
			// Separate chain, because this one cuts the highlights out on the first pass.
			blit(encoder, blurPipeline, bloomA, samplerInfoBlurFirst, config, pass -> {
				pass.setUniform("BlurDir", bloomDirHorizontal);
				pass.setUniform("InSampler", sceneCopy.getColorTextureView(), linear);
			});
			blit(encoder, blurPipeline, bloomB, samplerInfoBlurSecond, config, pass -> {
				pass.setUniform("BlurDir", bloomDirVertical);
				pass.setUniform("InSampler", bloomA.getColorTextureView(), linear);
			});
		}

		blit(encoder, compositePipeline, main, samplerInfoComposite, config, pass -> {
			pass.setUniform("InSampler", sceneCopy.getColorTextureView(), nearest);
			pass.setUniform("BlurSampler", blurB.getColorTextureView(), linear);
			pass.setUniform("BloomSampler", bloomB.getColorTextureView(), linear);
			pass.setUniform("DepthSampler", main.getDepthTextureView(), nearest);
		});
		configBuffer.rotate();
	}

	private interface PassSetup {
		void apply(RenderPass pass);
	}

	private static void blit(CommandEncoder encoder, RenderPipeline pipeline, RenderTarget target,
			GpuBuffer samplerInfo, GpuBuffer config, PassSetup setup) {
		try (RenderPass pass = encoder.createRenderPass(() -> "Absolute Cinema pass",
				target.getColorTextureView(), Optional.empty(), null, OptionalDouble.empty())) {
			pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("SamplerInfo", samplerInfo);
			pass.setUniform("CinemaConfig", config);
			setup.apply(pass);
			// One triangle over the whole target; the vertex shader makes it from the vertex index.
			pass.draw(3, 1, 0, 0);
		}
	}

	private static void release() {
		closeFramebuffers();
		closeSamplerInfo();
		if (configBuffer != null) {
			configBuffer.close();
			configBuffer = null;
		}
		if (blurDirHorizontal != null) {
			blurDirHorizontal.close();
			blurDirHorizontal = null;
		}
		if (blurDirVertical != null) {
			blurDirVertical.close();
			blurDirVertical = null;
		}
		if (bloomDirHorizontal != null) {
			bloomDirHorizontal.close();
			bloomDirHorizontal = null;
		}
		if (bloomDirVertical != null) {
			bloomDirVertical.close();
			bloomDirVertical = null;
		}
		width = 0;
		height = 0;
	}

	private static void closeFramebuffers() {
		if (sceneCopy != null) {
			sceneCopy.destroyBuffers();
			sceneCopy = null;
		}
		if (blurA != null) {
			blurA.destroyBuffers();
			blurA = null;
		}
		if (blurB != null) {
			blurB.destroyBuffers();
			blurB = null;
		}
		if (bloomA != null) {
			bloomA.destroyBuffers();
			bloomA = null;
		}
		if (bloomB != null) {
			bloomB.destroyBuffers();
			bloomB = null;
		}
	}

	private static void closeSamplerInfo() {
		if (samplerInfoBlurFirst != null) {
			samplerInfoBlurFirst.close();
			samplerInfoBlurFirst = null;
		}
		if (samplerInfoBlurSecond != null) {
			samplerInfoBlurSecond.close();
			samplerInfoBlurSecond = null;
		}
		if (samplerInfoComposite != null) {
			samplerInfoComposite.close();
			samplerInfoComposite = null;
		}
	}
}
