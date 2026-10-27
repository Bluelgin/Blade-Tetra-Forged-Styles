#version 150
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
in vec4 reflectionCoord;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    if (reflectionCoord.w <= 0.0) discard;
    vec2 uv = reflectionCoord.xy / reflectionCoord.w * 0.5 + 0.5;
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) discard;
    vec3 reflected = texture(Sampler0, uv).rgb;
    fragColor = vec4(reflected * vec3(0.97, 0.99, 1.0), 1.0) * vertexColor * ColorModulator;
}
