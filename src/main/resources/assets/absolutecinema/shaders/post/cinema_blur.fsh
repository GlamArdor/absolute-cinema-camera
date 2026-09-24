#version 150

uniform sampler2D InSampler;

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

layout(std140) uniform BlurDir {
    // xy direction, z "this is the bloom chain", w "cut the highlights out first"
    vec4 Direction;
};

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

vec3 fetch(vec2 uv) {
    vec3 c = texture(InSampler, uv).rgb;
    if (Direction.w > 0.5) {
        // Highlight extraction has to happen before the blur: afterwards a bright lamp has
        // already been averaged down with the dark room around it and no threshold can find it.
        float brightness = dot(c, LUMA);
        float excess = max(brightness - Extra2.y, 0.0) / max(1.0 - Extra2.y, 0.01);
        c *= excess;
    }
    return c;
}

const int TAPS = 12;

// A real gaussian, sampled evenly across its own width. Scaling the offsets of a fixed
// small-kernel gaussian instead would spread the taps apart and print several distinct copies of
// the frame – which reads as ghosting, not as blur.
void main() {
    float drive = Direction.z > 0.5 ? Extra2.z : Extra.y;
    float sigma = 1.0 + drive * 9.0;
    // Cover +-2 sigma; the step never exceeds two texels, so the taps still overlap.
    float spacing = min(2.0 * sigma / float(TAPS), 2.0);
    vec2 step = oneTexel * Direction.xy * spacing;

    vec3 sum = fetch(texCoord);
    float total = 1.0;

    for (int i = 1; i <= TAPS; i++) {
        float x = float(i) * spacing;
        float weight = exp(-0.5 * x * x / (sigma * sigma));
        sum += fetch(texCoord + step * float(i)) * weight;
        sum += fetch(texCoord - step * float(i)) * weight;
        total += weight * 2.0;
    }

    fragColor = vec4(sum / total, 1.0);
}
