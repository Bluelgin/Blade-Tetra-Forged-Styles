#version 150
uniform sampler2D DiffuseSampler;
uniform float Intensity;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec4 color = texture(DiffuseSampler, texCoord);
    float luma = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
    float red = smoothstep(0.02, 0.18, color.r - max(color.g, color.b));
    vec3 graded = mix(color.rgb, vec3(luma), 0.25 * Intensity * (1.0 - red));
    vec2 edge = texCoord * 2.0 - 1.0;
    float vignette = smoothstep(0.35, 1.6, dot(edge, edge));
    graded *= 1.0 - Intensity * (0.035 + vignette * 0.10);
    fragColor = vec4(graded, color.a);
}
