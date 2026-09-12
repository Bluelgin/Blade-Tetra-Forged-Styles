#version 150

uniform float RiftTime;
uniform float RiftIntensity;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float band(float value, float center, float halfWidth, float feather) {
    return 1.0 - smoothstep(halfWidth, halfWidth + feather, abs(value - center));
}

void main() {
    vec2 p = texCoord0 * 2.0 - 1.0;
    float seed = vertexColor.b * 251.0;
    float filled = smoothstep(0.45, 0.55, vertexColor.r);
    float flash = clamp(vertexColor.g, 0.0, 1.0);
    float life = clamp(vertexColor.a * RiftIntensity, 0.0, 1.0);

    float tipFade = 1.0 - smoothstep(0.72, 1.0, abs(p.y));
    if (tipFade <= 0.001 || life <= 0.001) {
        discard;
    }

    float drift = sin(p.y * 8.5 + seed * 0.73) * 0.060
            + sin(p.y * 21.0 - seed * 1.91) * 0.026
            + sin(p.y * 43.0 + seed * 0.37) * 0.012;
    drift += sin(p.y * 13.0 + RiftTime * 1.35 + seed) * 0.010;

    float taper = 0.72 + (1.0 - abs(p.y)) * 0.28;
    float distanceToCore = abs(p.x - drift);
    float coreWidth = (0.026 + flash * 0.012) * taper;
    float core = 1.0 - smoothstep(coreWidth, coreWidth + 0.013, distanceToCore);
    float outer = 1.0 - smoothstep(coreWidth + 0.055,
            coreWidth + 0.125, distanceToCore);
    float rim = max(outer - core * 0.52, 0.0);

    float branchGateA = smoothstep(-0.62, -0.20, p.y)
            * (1.0 - smoothstep(-0.10, 0.16, p.y));
    float branchGateB = smoothstep(0.02, 0.24, p.y)
            * (1.0 - smoothstep(0.50, 0.76, p.y));
    float branchA = (1.0 - smoothstep(0.020, 0.055,
            abs(p.x - drift + (p.y + 0.22) * 0.42))) * branchGateA;
    float branchB = (1.0 - smoothstep(0.018, 0.050,
            abs(p.x - drift - (p.y - 0.18) * 0.37))) * branchGateB;
    float branches = max(branchA, branchB) * 0.56;

    float voidFlow = 0.5 + 0.5 * sin(p.y * 27.0
            - RiftTime * 2.25 + seed * 2.7);
    float voidPulse = 0.82 + 0.18 * sin(RiftTime * 1.1 + seed * 0.31);

    float bladeCenter = p.x + 0.025 - p.y * 0.045;
    float blade = band(bladeCenter, 0.0, 0.020, 0.024)
            * (1.0 - smoothstep(0.58, 0.70, abs(p.y)));
    float guard = band(p.y, -0.28, 0.018, 0.025)
            * (1.0 - smoothstep(0.24, 0.36, abs(p.x)));
    float sword = max(blade, guard) * filled;

    float sweepY = mix(-0.72, 0.72, 1.0 - flash);
    float sweep = exp(-abs(p.y - sweepY) * 13.0)
            * flash * (core + rim + sword * 0.8);

    vec3 voidColor = mix(vec3(0.004, 0.001, 0.012),
            vec3(0.055, 0.012, 0.095), voidFlow * 0.55);
    vec3 rimColor = mix(vec3(0.44, 0.24, 0.78),
            vec3(0.93, 0.86, 1.0),
            clamp(0.22 + flash * 0.72 + rim * 0.16, 0.0, 1.0));
    vec3 swordColor = mix(vec3(0.38, 0.19, 0.62),
            vec3(0.98, 0.94, 1.0), 0.66 + flash * 0.34);

    vec3 color = voidColor * core * (0.86 + voidPulse * 0.14);
    color += rimColor * rim * (0.72 + flash * 0.88);
    color += rimColor * branches * (0.30 + flash * 0.45);
    color += swordColor * sword * (0.82 + flash * 0.74);
    color += vec3(0.93, 0.84, 1.0) * sweep * 0.82;

    float alpha = core * 0.94
            + rim * (0.50 + flash * 0.18)
            + branches * 0.38
            + sword * 0.78
            + sweep * 0.32;
    alpha = clamp(alpha * tipFade * life, 0.0, 1.0);

    if (alpha <= 0.002) {
        discard;
    }

    fragColor = vec4(color, alpha);
}
