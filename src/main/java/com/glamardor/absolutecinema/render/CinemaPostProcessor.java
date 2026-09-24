package com.glamardor.absolutecinema.render;

import com.glamardor.absolutecinema.AbsoluteCinema;
import com.glamardor.absolutecinema.CinemaManager;
import com.glamardor.absolutecinema.camera.CameraDirector;
import com.glamardor.absolutecinema.config.CinemaConfig;
import com.glamardor.absolutecinema.config.ColorGrade;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.MappableRingBuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.ProjectionMatrix2;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

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
	private static final float NEAR_PLANE = 0.05f;

	/** Luminance above which a pixel is treated as a light source worth glowing. */
	private static final float BLOOM_THRESHOLD = 0.55f;

	@Nullable
	private static RenderPipeline blurPipeline;
	@Nullable
	private static RenderPipeline compositePipeline;
	@Nullable
	private static ProjectionMatrix2 projection;

	@Nullable
	private static SimpleFramebuffer sceneCopy;
	@Nullable
	private static SimpleFramebuffer blurA;
	@Nullable
	private static SimpleFramebuffer blurB;
	@Nullable
	private static SimpleFramebuffer bloomA;
	@Nullable
	private static SimpleFramebuffer bloomB;

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

	public static void render(MinecraftClient client, Framebuffer main) {
		if (DEBUG && !loggedInvocation) {
			loggedInvocation = true;
			AbsoluteCinema.LOGGER.info("[filters] render hook reached, framebuffer {}x{}, depth={}",
					main.textureWidth, main.textureHeight, main.useDepthAttachment);
		}
		if (disabled || client.world == null) {
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

	private static void ensureResources(Framebuffer main) {
		if (blurPipeline == null) {
			blurPipeline = RenderPipeline.builder(RenderPipelines.POST_EFFECT_PROCESSOR_SNIPPET)
					.withLocation(AbsoluteCinema.id("pipeline/cinema_blur"))
					.withVertexShader(AbsoluteCinema.id("post/cinema_blur"))
					.withFragmentShader(AbsoluteCinema.id("post/cinema_blur"))
					.withSampler("InSampler")
					.withUniform("SamplerInfo", UniformType.UNIFORM_BUFFER)
					.withUniform("CinemaConfig", UniformType.UNIFORM_BUFFER)
					.withUniform("BlurDir", UniformType.UNIFORM_BUFFER)
					.build();
			compositePipeline = RenderPipeline.builder(RenderPipelines.POST_EFFECT_PROCESSOR_SNIPPET)
					.withLocation(AbsoluteCinema.id("pipeline/cinema_composite"))
					.withVertexShader(AbsoluteCinema.id("post/cinema_composite"))
					.withFragmentShader(AbsoluteCinema.id("post/cinema_composite"))
					.withSampler("InSampler")
					.withSampler("BlurSampler")
					.withSampler("BloomSampler")
					.withSampler("DepthSampler")
					.withUniform("SamplerInfo", UniformType.UNIFORM_BUFFER)
					.withUniform("CinemaConfig", UniformType.UNIFORM_BUFFER)
					.build();
		}
		if (projection == null) {
			// Same parameters vanilla's ShaderLoader uses for post effects – invertY stays false.
			projection = new ProjectionMatrix2("absolute cinema", 0.1f, 1000.0f, false);
		}
		if (configBuffer == null) {
			configBuffer = new MappableRingBuffer(() -> "cinema config", UNIFORM_MAPPABLE, CONFIG_BYTES);
			// z marks the bloom chain (its own radius), w asks for the highlight cut.
			blurDirHorizontal = createVec4("cinema blur dir h", 1.0f, 0.0f, 0.0f, 0.0f);
			blurDirVertical = createVec4("cinema blur dir v", 0.0f, 1.0f, 0.0f, 0.0f);
			bloomDirHorizontal = createVec4("cinema bloom dir h", 1.0f, 0.0f, 1.0f, 1.0f);
			bloomDirVertical = createVec4("cinema bloom dir v", 0.0f, 1.0f, 1.0f, 0.0f);
		}

		int targetWidth = main.textureWidth;
		int targetHeight = main.textureHeight;
		if (sceneCopy != null && targetWidth == width && targetHeight == height) {
			return;
		}

		width = targetWidth;
		height = targetHeight;
		int halfWidth = Math.max(1, width / 2);
		int halfHeight = Math.max(1, height / 2);

		closeFramebuffers();
		sceneCopy = new SimpleFramebuffer("cinema scene", width, height, false);
		blurA = new SimpleFramebuffer("cinema blur a", halfWidth, halfHeight, false);
		blurB = new SimpleFramebuffer("cinema blur b", halfWidth, halfHeight, false);
		bloomA = new SimpleFramebuffer("cinema bloom a", halfWidth, halfHeight, false);
		bloomB = new SimpleFramebuffer("cinema bloom b", halfWidth, halfHeight, false);

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
	private static void updateFocus(MinecraftClient client, CinemaConfig config) {
		float target = config.dofFocusDistance;
		if (config.dofAutoFocus) {
			target = measureSubjectDistance(client, config);
		}
		float dt = CinemaManager.getFrameDelta();
		float step = 1.0f - (float) Math.exp(-dt / 0.35f);
		focusDistance = MathHelper.lerp(step, focusDistance, target);
	}

	private static float measureSubjectDistance(MinecraftClient client, CinemaConfig config) {
		Camera camera = client.gameRenderer.getCamera();
		Vec3d origin = camera.getPos();

		if (CinemaManager.isDirecting()) {
			double toSubject = CameraDirector.get().getLook().distanceTo(origin);
			if (toSubject > 0.4) {
				return (float) toSubject;
			}
		}

		if (client.world == null) {
			return config.dofFocusDistance;
		}
		Vec3d direction = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
		Vec3d end = origin.add(direction.multiply(64.0));
		HitResult hit = client.world.raycast(new RaycastContext(origin, end,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, client.player));
		if (hit.getType() == HitResult.Type.MISS) {
			return 32.0f;
		}
		return (float) MathHelper.clamp(hit.getPos().distanceTo(origin), 0.6, 64.0);
	}

	private static void writeConfig(MinecraftClient client, CinemaConfig config, float weight,
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
		float far = client.gameRenderer.getFarPlaneDistance();
		float aspect = height == 0 ? 1.0f : (float) width / height;

		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		try (GpuBuffer.MappedView view = encoder.mapBuffer(configBuffer.getBlocking(), false, true)) {
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

	private static void runPasses(Framebuffer main, boolean wantsDof, boolean wantsBloom) {
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		GpuBuffer config = configBuffer.getBlocking();

		encoder.copyTextureToTexture(main.getColorAttachment(), sceneCopy.getColorAttachment(),
				0, 0, 0, 0, 0, width, height);

		RenderSystem.backupProjectionMatrix();
		try {
			sceneCopy.setFilter(FilterMode.LINEAR);

			if (wantsDof) {
				blit(encoder, blurPipeline, blurA, samplerInfoBlurFirst, config, pass -> {
					pass.setUniform("BlurDir", blurDirHorizontal);
					pass.bindSampler("InSampler", sceneCopy.getColorAttachmentView());
				});

				blurA.setFilter(FilterMode.LINEAR);
				blit(encoder, blurPipeline, blurB, samplerInfoBlurSecond, config, pass -> {
					pass.setUniform("BlurDir", blurDirVertical);
					pass.bindSampler("InSampler", blurA.getColorAttachmentView());
				});
			}

			if (wantsBloom) {
				// Separate chain, because this one cuts the highlights out on the first pass.
				blit(encoder, blurPipeline, bloomA, samplerInfoBlurFirst, config, pass -> {
					pass.setUniform("BlurDir", bloomDirHorizontal);
					pass.bindSampler("InSampler", sceneCopy.getColorAttachmentView());
				});

				bloomA.setFilter(FilterMode.LINEAR);
				blit(encoder, blurPipeline, bloomB, samplerInfoBlurSecond, config, pass -> {
					pass.setUniform("BlurDir", bloomDirVertical);
					pass.bindSampler("InSampler", bloomA.getColorAttachmentView());
				});
			}

			sceneCopy.setFilter(FilterMode.NEAREST);
			blurB.setFilter(FilterMode.LINEAR);
			bloomB.setFilter(FilterMode.LINEAR);
			blitToMain(encoder, main, config);
		} finally {
			RenderSystem.restoreProjectionMatrix();
		}
	}

	private interface PassSetup {
		void apply(RenderPass pass);
	}

	private static void blit(CommandEncoder encoder, RenderPipeline pipeline, Framebuffer target,
			GpuBuffer samplerInfo, GpuBuffer config, PassSetup setup) {
		RenderSystem.setProjectionMatrix(projection.set(target.textureWidth, target.textureHeight),
				ProjectionType.ORTHOGRAPHIC);
		try (RenderPass pass = encoder.createRenderPass(() -> "Absolute Cinema pass",
				target.getColorAttachmentView(), OptionalInt.empty(), null, OptionalDouble.empty())) {
			pass.setPipeline(pipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("SamplerInfo", samplerInfo);
			pass.setUniform("CinemaConfig", config);
			setup.apply(pass);
			bindQuad(pass);
			pass.drawIndexed(0, 0, 6, 1);
		}
	}

	private static void blitToMain(CommandEncoder encoder, Framebuffer main, GpuBuffer config) {
		RenderSystem.setProjectionMatrix(projection.set(main.textureWidth, main.textureHeight),
				ProjectionType.ORTHOGRAPHIC);
		try (RenderPass pass = encoder.createRenderPass(() -> "Absolute Cinema composite",
				main.getColorAttachmentView(), OptionalInt.empty(), null, OptionalDouble.empty())) {
			pass.setPipeline(compositePipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("SamplerInfo", samplerInfoComposite);
			pass.setUniform("CinemaConfig", config);
			pass.bindSampler("InSampler", sceneCopy.getColorAttachmentView());
			pass.bindSampler("BlurSampler", blurB.getColorAttachmentView());
			pass.bindSampler("BloomSampler", bloomB.getColorAttachmentView());
			pass.bindSampler("DepthSampler", main.getDepthAttachmentView());
			bindQuad(pass);
			pass.drawIndexed(0, 0, 6, 1);
		}
		configBuffer.rotate();
	}

	private static void bindQuad(RenderPass pass) {
		RenderSystem.ShapeIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
		pass.setVertexBuffer(0, RenderSystem.getQuadVertexBuffer());
		pass.setIndexBuffer(indices.getIndexBuffer(6), indices.getIndexType());
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
		if (projection != null) {
			projection.close();
			projection = null;
		}
		width = 0;
		height = 0;
	}

	private static void closeFramebuffers() {
		if (sceneCopy != null) {
			sceneCopy.delete();
			sceneCopy = null;
		}
		if (blurA != null) {
			blurA.delete();
			blurA = null;
		}
		if (blurB != null) {
			blurB.delete();
			blurB = null;
		}
		if (bloomA != null) {
			bloomA.delete();
			bloomA = null;
		}
		if (bloomB != null) {
			bloomB.delete();
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
