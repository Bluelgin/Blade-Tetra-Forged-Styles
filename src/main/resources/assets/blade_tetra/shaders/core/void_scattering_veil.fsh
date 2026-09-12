#version 150

uniform float VeilTime;
uniform float VeilIntensity;
uniform float Opening;
uniform float CaptureFlash;
uniform float Instability;
uniform float StoredRatio;
uniform float Collapse;
uniform float Aspect;
uniform vec2 ImpactScreen;

in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    vec2 p = texCoord0 * 2.0 - 1.0;
    p.x *= Aspect;

    float radius = length(p);
    float edge = smoothstep(0.58, 1.08, radius);
    float hardEdge = smoothstep(0.83, 1.22, radius);
    float centerClear = smoothstep(0.48, 0.74, radius);

    float breathing = 0.5 + 0.5 * sin(VeilTime * 1.15);
    float flowA = sin(p.x * 15.0 + p.y * 21.0 + VeilTime * 1.35);
    float flowB = sin(p.x * 31.0 - p.y * 13.0 - VeilTime * 0.72);
    float flow = flowA * 0.5 + flowB * 0.5;

    float crackWave = abs(sin(p.x * 10.0 + p.y * 18.0
            + sin(p.y * 9.0 + VeilTime * 0.22) * 2.1));
    float cracks = 1.0 - smoothstep(0.035, 0.105, crackWave);
    cracks *= smoothstep(0.61, 0.84, radius);
    cracks *= 0.32 + Instability * 0.68;

    vec2 impact = ImpactScreen;
    impact.x *= Aspect;
    float impactDistance = length(p - impact);
    float impactPulse = exp(-impactDistance * (7.5 - Instability * 1.4));
    impactPulse *= CaptureFlash;

    float openingRingRadius = mix(1.34, 0.72, Opening);
    float openingRing = 1.0 - smoothstep(0.025, 0.075,
            abs(radius - openingRingRadius));
    openingRing *= (1.0 - Opening) * 1.25;

    float storedGlow = StoredRatio * smoothstep(0.69, 0.93, radius);
    float unstablePulse = Instability * (0.55 + breathing * 0.45)
            * smoothstep(0.72, 1.08, radius);
    float collapsePulse = Collapse * smoothstep(0.52, 1.05, radius);

    vec3 voidBlack = vec3(0.008, 0.004, 0.014);
    vec3 deepViolet = vec3(0.060, 0.025, 0.092);
    vec3 edgeViolet = vec3(0.46, 0.29, 0.72);
    vec3 coldWhite = vec3(0.92, 0.86, 1.00);

    vec3 color = mix(voidBlack, deepViolet,
            0.34 + flow * 0.07 + StoredRatio * 0.06);
    color += edgeViolet * cracks * (0.23 + Instability * 0.42);
    color += edgeViolet * storedGlow * 0.12;
    color += edgeViolet * unstablePulse * 0.13;
    color += coldWhite * impactPulse * (0.38 + Instability * 0.18);
    color += coldWhite * openingRing * 0.20;
    color += coldWhite * collapsePulse * 0.18;

    float alpha = edge * (0.075 + Instability * 0.055 + StoredRatio * 0.025);
    alpha += hardEdge * (0.035 + Instability * 0.045);
    alpha += cracks * (0.030 + Instability * 0.050);
    alpha += impactPulse * 0.12;
    alpha += openingRing * 0.045;
    alpha += collapsePulse * 0.085;
    alpha += flow * 0.006 * edge;

    alpha *= centerClear * Opening * VeilIntensity;
    alpha = clamp(alpha, 0.0, 0.31);

    if (alpha <= 0.002) {
        discard;
    }

    fragColor = vec4(max(color, vec3(0.0)), alpha * vertexColor.a);
}
