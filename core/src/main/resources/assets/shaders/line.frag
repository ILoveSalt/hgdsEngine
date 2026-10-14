#version 330 core

in vec4 vColor;

uniform vec4 uOverrideColor;
uniform bool uUseOverride;

out vec4 FragColor;

void main() {
    FragColor = uUseOverride ? uOverrideColor : vColor;
}