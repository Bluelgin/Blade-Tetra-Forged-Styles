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
    float flowC = sin(uv.y * 11.0 + uv.x * 47.0 + DomeTime * 0.31 + seed * 0.41);
    float flow = flowA * 0.42 + flowB * 0.38 + flowC * 0.20;

    float crackWave = abs(sin(uv.y * 23.0 + sin(uv.x * 19.0 + seed) * 2.4
            + DomeTime * 0.16));
    float cracks = 1.0 - smoothstep(0.030, 0.125, crackWave);
    cracks *= 0.42 + 0.58 * abs(sin(uv.x * 41.0 + uv.y * 9.0 + seed * 0.7));

    float secondaryWave = abs(sin(uv.x * 27.0 - uv.y * 17.0
            + sin(uv.y * 13.0 + seed * 0.31) * 1.8 - DomeTime * 0.11));
    float secondaryCracks = 1.0 - smoothstep(0.025, 0.095, secondaryWave);
    secondaryCracks *= 0.35 + 0.65 * abs(sin(uv.y * 37.0 + seed));

    float du = wrappedDistance(uv.x, ImpactUv.x);
    float dv = uv.y - ImpactUv.y;
    float impactDistance = sqrt(du * du + dv * dv);
    float impactProgress = 1.0 - clamp(ImpactFlash, 0.0, 1.0);
    float ringRadius = mix(0.018, 0.24, impactProgress);
    float ripple = 1.0 - smoothstep(0.016, 0.052,
            abs(impactDistance - ringRadius));
    ripple *= clamp(ImpactFlash * 1.50, 0.0, 1.0);
    float impactCore = exp(-impactDistance * 18.0) * ImpactFlash;

    float equator = 1.0 - smoothstep(0.025, 0.12, abs(uv.y - 0.5));
    float breathing = 0.5 + 0.5 * sin(DomeTime * 0.95 + seed * 0.03);
    float unstable = Collapse * (0.58 + breathing * 0.42);

    vec3 voidBlack = vec3(0.006, 0.003, 0.012);
    vec3 deepViolet = vec3(0.062, 0.026, 0.096);
    vec3 edgeViolet = vec3(0.60, 0.42, 0.88);
    vec3 coldWhite = vec3(0.94, 0.88, 1.00);

    vec3 color = mix(voidBlack, deepViolet, 0.45 + flow * 0.10);
    color += edgeViolet * cracks * (0.24 + Collapse * 0.30);
    color += edgeViolet * secondaryCracks * (0.09 + Collapse * 0.19);
    color += edgeViolet * equator * 0.035;
    color += coldWhite * ripple * 0.52;
    color += coldWhite * impactCore * 0.20;
    color += edgeViolet * unstable * 0.12;

    float alpha = DomeOpacity * life * 1.52
            * (0.86 + flow * 0.08 + cracks * 0.35 + secondaryCracks * 0.12);
    alpha += ripple * 0.145;
    alpha += impactCore * 0.055;
    alpha += equator * DomeOpacity * life * 0.10;
    alpha += unstable * DomeOpacity * life * 0.30;
    alpha = clamp(alpha, 0.0, 0.50);

    fragColor = vec4(max(color, vec3(0.0)), alpha);
}
