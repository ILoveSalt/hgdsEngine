#version 330 core

layout (location = 0) in vec3 aPos;
layout (location = 1) in vec2 aUV;
layout (location = 2) in float aBrightness;

uniform mat4 uProjection;
uniform mat4 uView;

out vec2 vUV;
out float vBrightness;

void main() {
    gl_Position = uProjection * uView * vec4(aPos, 1.0);
    vUV = aUV;
    vBrightness = aBrightness;
}