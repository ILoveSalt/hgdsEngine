#version 330 core

in vec2 vUV;
in float vBrightness;

uniform sampler2D uTexture;
uniform float uBrightness;

out vec4 FragColor;

void main() {
    vec4 texColor = texture(uTexture, vUV);
    // Прозрачные пиксели (листва) не рисуем — без этого сквозь них виден «мусор» глубины
    if (texColor.a < 0.5) discard;
    FragColor = vec4(texColor.rgb * vBrightness * uBrightness, texColor.a);
}