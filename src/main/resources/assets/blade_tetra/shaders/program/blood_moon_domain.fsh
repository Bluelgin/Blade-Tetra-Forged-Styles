#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float DomainIntensity;
uniform float DomainTime;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 centered = texCoord - vec2(0.5);
    centered.x *= OutSize.x / max(OutSize.y, 1.0);
    float edge = smoothstep(0.28, 0.92, length(centered));

    // A restrained peripheral heat drift keeps the domain alive without making
    // combat telegraphs or the center of the screen difficult to read.
    float heat = sin(texCoord.y * 54.0 + DomainTime * 1.7)
            * cos(texCoord.x * 31.0 - DomainTime * 1.1);
    vec2 sampleUv = texCoord + vec2(heat * edge * DomainIntensity * 0.00055, 0.0);
    vec3 source = texture(DiffuseSampler, sampleUv).rgb;
    float luminance = dot(source, vec3(0.2126, 0.7152, 0.0722));

    vec3 warm = vec3(
            luminance * 1.08 + source.r * 0.24,
            luminance * 0.50 + source.g * 0.12,
            luminance * 0.22 + source.b * 0.055);
    float preserveDark = smoothstep(0.025, 0.20, luminance);
    float grade = DomainIntensity * 0.68 * preserveDark;
    vec3 color = mix(source, warm, grade);

    float vignette = edge * edge * DomainIntensity;
    color *= 1.0 - vignette * 0.16;
    color += vec3(0.075, 0.012, 0.0) * vignette;

    fragColor = vec4(max(color, vec3(0.0)), 1.0);
}
