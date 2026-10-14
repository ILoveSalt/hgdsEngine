#version 330 core
in vec2 vUV;
in vec4 vColor;

uniform sampler2D uTex;
uniform float uBrightness;

out vec4 FragColor;

void main() {
    vec4 color = texture(uTex, vUV) * vColor;
    if (color.a < 0.004) discard;
    FragColor = vec4(color.rgb * uBrightness, color.a);
}