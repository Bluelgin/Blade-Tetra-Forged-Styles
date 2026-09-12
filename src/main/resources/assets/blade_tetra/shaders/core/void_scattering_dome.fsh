#version 150

uniform float DomeTime;
uniform float DomeIntensity;
uniform float DomeOpacity;
uniform vec2 ImpactUv;
uniform float ImpactFlash;
uniform float Collapse;

in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

float wrappedDistance(float a, float b) {
    float d = abs(a - b);
    return min(d, 1.0 - d);
}

void main() {
    float life = clamp(vertexColor.a * DomeIntensity, 0.0, 1.0);
    if (life <= 0.001) {
        discard;
    }

    vec2 uv = texCoord0;
    float seed = vertexColor.b * 251.0;
    float flowA = sin(uv.y * 31.0 + uv.x * 17.0 + DomeTime * 0.85 + seed * 0.11);
    float flowB = sin(uv.y * 57.0 - uv.x * 29.0 - DomeTime * 0.55 + seed * 0.23);
    float flow = flowA * 0.5 + flowB * 0.5;

    float crackWave = abs(sin(uv.y * 23.0 + sin(uv.x * 19.0 + seed) * 2.4
            + DomeTime * 0.16));
    float cracks = 1.0 - smoothstep(0.035, 0.115, crackWave);
    cracks *= 0.45 + 0.55 * abs(sin(uv.x * 41.0 + uv.y * 9.0 + seed * 0.7));

    float du = wrappedDistance(uv.x, ImpactUv.x);
    float dv = uv.y - ImpactUv.y;
    float impactDistance = sqrt(du * du + dv * dv);
    float impactProgress = 1.0 - clamp(ImpactFlash, 0.0, 1.0);
    float ringRadius = mix(0.018, 0.19, impactProgress);
    float ripple = 1.0 - smoothstep(0.018, 0.043,
            abs(impactDistance - ringRadius));
    ripple *= clamp(ImpactFlash * 1.35, 0.0, 1.0);

    vec3 voidBlack = vec3(0.010, 0.006, 0.016);
    vec3 deepViolet = vec3(0.055, 0.025, 0.085);
    vec3 edgeViolet = vec3(0.56, 0.40, 0.82);
    vec3 coldWhite = vec3(0.90, 0.84, 1.00);

    vec3 color = mix(voidBlack, deepViolet, 0.42 + flow * 0.10);
    color += edgeViolet * cracks * (0.16 + Collapse * 0.25);
    color += coldWhite * ripple * 0.34;
    color += edgeViolet * Collapse * 0.075;

    float alpha = DomeOpacity * life * (0.78 + flow * 0.06 + cracks * 0.22);
    alpha += ripple * 0.085;
    alpha += Collapse * DomeOpacity * life * 0.16;
    alpha = clamp(alpha, 0.0, 0.34);

    fragColor = vec4(max(color, vec3(0.0)), alpha);
}
