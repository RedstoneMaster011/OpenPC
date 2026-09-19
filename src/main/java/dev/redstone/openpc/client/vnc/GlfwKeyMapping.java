package dev.redstone.openpc.client.vnc;

import org.lwjgl.glfw.GLFW;

public final class GlfwKeyMapping {

    private GlfwKeyMapping() {
    }

    public static int keysym(int glfwKeycode, int modifiers) {
        if (glfwKeycode >= GLFW.GLFW_KEY_A && glfwKeycode <= GLFW.GLFW_KEY_Z) {
            int letter = 'a' + (glfwKeycode - GLFW.GLFW_KEY_A);
            return letter;
        }
        if (glfwKeycode >= GLFW.GLFW_KEY_0 && glfwKeycode <= GLFW.GLFW_KEY_9) {
            return '0' + (glfwKeycode - GLFW.GLFW_KEY_0);
        }
        if (glfwKeycode >= GLFW.GLFW_KEY_F1 && glfwKeycode <= GLFW.GLFW_KEY_F12) {
            return 0xFFBE + (glfwKeycode - GLFW.GLFW_KEY_F1);
        }
        if (glfwKeycode >= GLFW.GLFW_KEY_KP_0 && glfwKeycode <= GLFW.GLFW_KEY_KP_9) {
            return 0xFFB0 + (glfwKeycode - GLFW.GLFW_KEY_KP_0);
        }
        return switch (glfwKeycode) {
            case JS_ESCAPE -> 0xFF1B;
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> 0xFF0D;
            case GLFW.GLFW_KEY_TAB -> 0xFF09;
            case GLFW.GLFW_KEY_BACKSPACE -> 0xFF08;
            case GLFW.GLFW_KEY_INSERT -> 0xFF63;
            case GLFW.GLFW_KEY_DELETE -> 0xFFFF;
            case GLFW.GLFW_KEY_RIGHT -> 0xFF53;
            case GLFW.GLFW_KEY_LEFT -> 0xFF51;
            case GLFW.GLFW_KEY_DOWN -> 0xFF54;
            case GLFW.GLFW_KEY_UP -> 0xFF52;
            case GLFW.GLFW_KEY_PAGE_UP -> 0xFF55;
            case GLFW.GLFW_KEY_PAGE_DOWN -> 0xFF56;
            case GLFW.GLFW_KEY_HOME -> 0xFF50;
            case GLFW.GLFW_KEY_END -> 0xFF57;
            case GLFW.GLFW_KEY_SPACE -> 0x20;
            case GLFW.GLFW_KEY_LEFT_SHIFT -> 0xFFE1;
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> 0xFFE2;
            case GLFW.GLFW_KEY_LEFT_CONTROL -> 0xFFE3;
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> 0xFFE4;
            case GLFW.GLFW_KEY_LEFT_ALT -> 0xFFE9;
            case GLFW.GLFW_KEY_RIGHT_ALT -> 0xFFEA;
            case GLFW.GLFW_KEY_LEFT_SUPER -> 0xFFEB;
            case GLFW.GLFW_KEY_RIGHT_SUPER -> 0xFFEC;
            case GLFW.GLFW_KEY_CAPS_LOCK -> 0xFFE5;
            case GLFW.GLFW_KEY_MINUS -> 0x2D;
            case GLFW.GLFW_KEY_EQUAL -> 0x3D;
            case GLFW.GLFW_KEY_LEFT_BRACKET -> 0x5B;
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> 0x5D;
            case GLFW.GLFW_KEY_BACKSLASH -> 0x5C;
            case GLFW.GLFW_KEY_SEMICOLON -> 0x3B;
            case GLFW.GLFW_KEY_APOSTROPHE -> 0x27;
            case GLFW.GLFW_KEY_GRAVE_ACCENT -> 0x60;
            case GLFW.GLFW_KEY_COMMA -> 0x2C;
            case GLFW.GLFW_KEY_PERIOD -> 0x2E;
            case GLFW.GLFW_KEY_SLASH -> 0x2F;
            default -> 0;
        };
    }

    public static int keysymWithShift(int glfwKeycode, int modifiers) {
        int plain = keysym(glfwKeycode, modifiers);
        if (plain <= 0) {
            return 0;
        }
        boolean shifted = (modifiers & 1) != 0;
        if (plain >= 'a' && plain <= 'z') {
            return shifted ? plain - 32 : plain;
        }
        if (shifted) {
            return switch (plain) {
                case '1' -> '!';
                case '2' -> '@';
                case '3' -> '#';
                case '4' -> '$';
                case '5' -> '%';
                case '6' -> '^';
                case '7' -> '&';
                case '8' -> '*';
                case '9' -> '(';
                case '0' -> ')';
                case '-' -> '_';
                case '=' -> '+';
                case '[' -> '{';
                case ']' -> '}';
                case '\\' -> '|';
                case ';' -> ':';
                case '\'' -> '"';
                case ',' -> '<';
                case '.' -> '>';
                case '/' -> '?';
                case '`' -> '~';
                default -> plain;
            };
        }
        return plain;
    }

    private static final int JS_ESCAPE = GLFW.GLFW_KEY_ESCAPE;
}