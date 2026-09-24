#version 150

uniform sampler2D InSampler;
uniform sampler2D BlurSampler;
uniform sampler2D BloomSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform CinemaConfig {
    vec4 Lift;
    vec4 Gamma;
    vec4 Gain;
    vec4 Look;   // x saturation, y contrast, z vignette, w grain
    vec4 Focus;  // x focus distance, y focus range, z blur strength, w time
    vec4 Screen; // x near, y far, z aspect, w grade strength
    vec4 Extra;  // x foreground blur amount, y dof radius drive, z bloom, w aberration
    vec4 Extra2; // x flicker, y bloom threshold, z bloom radius drive, w image warp
    vec4 Extra3; // x double vision, yzw spare
};

in vec2 texCoord;

out vec4 fragColor;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

float linearDepth(float rawDepth) {
    float near = Screen.x;
    float far = Screen.y;
    float z = rawDepth * 2.0 - 1.0;
    return (2.0 * near * far) / (far + near - z * (far - near));
}

// Integer hash. The usual fract(sin(dot(...))) breaks down once the coordinates get large –
// float precision collapses and the "noise" turns into diagonal bands crawling across the frame.
uint hashInt(uint x) {
    x ^= x >> 17;
    x *= 0xed5ad4bbu;
    x ^= x >> 11;
    x *= 0xac4c1b51u;
    x ^= x >> 15;
    x *= 0x31848babu;
    x ^= x >> 14;
    return x;
}

/** White noise per screen pixel per frame, evenly distributed over the whole image. */
float pixelNoise(uvec2 pixel, uint frame) {
    uint h = hashInt(pixel.x ^ hashInt(pixel.y ^ hashInt(frame)));
    return float(h) * (1.0 / 4294967296.0);
}

void main() {
    // --- image warp ----------------------------------------------------------------------
    // Two slow waves running across each other make the whole picture swim, which is what
    // actually sells a fever. Everything downstream samples through this offset, depth
    // included, so the focus does not come apart from the image.
    vec2 uv = texCoord;
    if (Extra2.w > 0.00001) {
        float t = Focus.w;
        uv += vec2(
            sin(texCoord.y * 14.0 + t * 1.3) + sin(texCoord.y * 5.0 - t * 0.7) * 0.6,
            cos(texCoord.x * 11.0 + t * 1.1) + cos(texCoord.x * 4.0 - t * 0.5) * 0.6) * Extra2.w;
        uv = clamp(uv, vec2(0.0), vec2(1.0));
    }

    // --- chromatic aberration ----------------------------------------------------------
    // The red and blue channels are sampled slightly toward/away from the centre, so the
    // fringing grows with the distance from it – the way a real fast lens misbehaves.
    vec3 sharp;
    if (Extra.w > 0.00001) {
        vec2 fromCentre = uv - 0.5;
        vec2 shift = fromCentre * Extra.w;
        sharp = vec3(
            texture(InSampler, clamp(uv + shift, 0.0, 1.0)).r,
            texture(InSampler, uv).g,
            texture(InSampler, clamp(uv - shift, 0.0, 1.0)).b);
    } else {
        sharp = texture(InSampler, uv).rgb;
    }
    // --- double vision -------------------------------------------------------------------
    // A second copy of the frame, offset along a slowly rotating direction. Lighten-blended so
    // it reads as an eye that cannot converge rather than as a dirty lens.
    if (Extra3.x > 0.001) {
        float t = Focus.w * 0.55;
        vec2 offset = vec2(cos(t), sin(t * 0.83)) * 0.013;
        vec3 ghost = texture(InSampler, clamp(uv + offset, 0.0, 1.0)).rgb;
        sharp = mix(sharp, max(sharp, ghost), Extra3.x);
    }

    vec3 blurred = texture(BlurSampler, uv).rgb;

    // --- depth of field -------------------------------------------------------------------
    vec3 color = sharp;
    if (Focus.z > 0.001) {
        float distance = linearDepth(texture(DepthSampler, uv).r);
        float offset = distance - Focus.x;

        // Behind the focus plane the world falls off gradually; in front of it a real lens goes
        // soft much faster, which reads as ghosting on the ground at your feet, so it is scaled
        // down (and off entirely by default).
        float behind = max(offset - Focus.y, 0.0) / max(Focus.y * 2.5, 1.0);
        float front = max(-offset - Focus.y * 0.6, 0.0) / max(Focus.y * 1.5, 1.0) * Extra.x;

        float coc = clamp(max(behind, front), 0.0, 1.0);
        // Ease the transition so the sharp zone melts into the soft one.
        coc = coc * coc * (3.0 - 2.0 * coc);
        color = mix(sharp, blurred, coc * Focus.z);
    }
    vec3 ungraded = color;

    // --- bloom / halation -----------------------------------------------------------------
    // Its own chain: the highlights were cut out first and only then blurred, so this texture
    // already holds nothing but glow. Screen blending keeps it from clipping straight to white.
    if (Extra.z > 0.001) {
        vec3 glow = texture(BloomSampler, uv).rgb * Extra.z;
        color = 1.0 - (1.0 - color) * (1.0 - glow);
    }

    // --- three way colour corrector -------------------------------------------------------
    color = color * Gain.rgb + Lift.rgb;
    color = pow(max(color, vec3(0.0)), 1.0 / max(Gamma.rgb, vec3(0.01)));

    float luma = dot(color, LUMA);
    color = mix(vec3(luma), color, Look.x);
    color = (color - 0.5) * Look.y + 0.5;

    // --- gate flicker ---------------------------------------------------------------------
    // Slow: a candle breathes at well under a hertz. The earlier version ran at 4 and 10 Hz,
    // which the eye reads as flicker-fusion shimmer rather than as light moving.
    if (Extra2.x > 0.001) {
        float t = Focus.w;
        float wobble = sin(t * 2.3) * 0.62 + sin(t * 5.7 + 1.9) * 0.38;
        color *= 1.0 + wobble * Extra2.x;
    }

    // --- vignette -------------------------------------------------------------------------
    if (Look.z > 0.001) {
        vec2 centred = (texCoord - 0.5) * vec2(Screen.z, 1.0);
        float falloff = smoothstep(0.75, 0.18, length(centred));
        color *= mix(1.0, falloff, Look.z);
    }

    // --- film grain -----------------------------------------------------------------------
    // Keyed to the actual pixel grid and to a frame counter, so the grain is uniform across the
    // frame and resamples every frame instead of drifting as a pattern.
    if (Look.w > 0.001) {
        uvec2 pixel = uvec2(gl_FragCoord.xy);
        uint frame = uint(Focus.w * 60.0);
        float noise = pixelNoise(pixel, frame);
        color += (noise - 0.5) * Look.w;
    }

    color = mix(ungraded, color, Screen.w);
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
