#include <windows.h>
#include <roapi.h> // Para RoInitialize
#include <shellapi.h> // Para System Tray
#include <shlobj.h> // Para %LOCALAPPDATA%
#include <string>
#include <fstream>
#include "json.hpp" // Librería externa para JSON

// Identificadores para nuestra aplicación
#define ID_TRAY_ICON 1001
#define WM_TRAYICON (WM_USER + 1)
#define IDM_CAPTURE 2001
#define IDM_CLIPBOARD 2002
#define IDM_TOGGLE_MODE 2003
#define IDM_EXIT 2004

#define HOTKEY_CAPTURE 1
#define HOTKEY_CLIPBOARD 2

HWND hwndFloating = NULL;
HWND hwndFocus = NULL;
bool isFocusMode = false;

// 5. Función para guardar y cargar configuración en %LOCALAPPDATA%
void ManageSettings(bool save) {
    char localAppData[MAX_PATH];
    if (SUCCEEDED(SHGetFolderPathA(NULL, CSIDL_LOCAL_APPDATA, NULL, 0, localAppData))) {
        std::string path = std::string(localAppData) + "\\DyslexiaReader";
        CreateDirectoryA(path.c_str(), NULL); // Crea la carpeta si no existe
        path += "\\reading-profile.json";
        ////////////////
        nlohmann::json perfil;
        perfil["mode"] = "floating";
        perfil["fontSize"] = 18;

        std::ofstream archivo_salida(path);
        if (archivo_salida.is_open()) {
            archivo_salida << perfil.dump(4); // El 4 indenta el texto para que sea legible
            archivo_salida.close();
        }
        /////////////////
    }
}

// Procedimiento de ventana (Maneja los eventos, clics y atajos de teclado)
LRESULT CALLBACK WindowProc(HWND hwnd, UINT uMsg, WPARAM wParam, LPARAM lParam) {
    switch (uMsg) {
    case WM_HOTKEY: {
        // 3. Manejo de atajos globales
        if (wParam == HOTKEY_CAPTURE) {
            // Ctrl + Shift + Space presionado: Lanzar recorte de pantalla
        }
        else if (wParam == HOTKEY_CLIPBOARD) {
            // Ctrl + Shift + V presionado: Leer portapapeles
            if (OpenClipboard(nullptr)) {
                HANDLE hData = GetClipboardData(CF_UNICODETEXT);
                if (hData != nullptr) {
                    wchar_t* text = static_cast<wchar_t*>(GlobalLock(hData));
                    // Procesar el texto para el lector...
                    GlobalUnlock(hData);
                }
                CloseClipboard();
            }
        }
        return 0;
    }

    case WM_TRAYICON: {
        // 2. Manejo de clics en el icono de la bandeja del sistema
        if (lParam == WM_RBUTTONUP) {
            POINT pt;
            GetCursorPos(&pt);
            HMENU hMenu = CreatePopupMenu();
            InsertMenu(hMenu, -1, MF_BYPOSITION | MF_STRING, IDM_CAPTURE, L"Capture Screen");
            InsertMenu(hMenu, -1, MF_BYPOSITION | MF_STRING, IDM_CLIPBOARD, L"Read Clipboard");
            InsertMenu(hMenu, -1, MF_BYPOSITION | MF_STRING, IDM_TOGGLE_MODE, L"Switch Mode");
            InsertMenu(hMenu, -1, MF_BYPOSITION | MF_STRING, IDM_EXIT, L"Exit");

            SetForegroundWindow(hwnd);
            TrackPopupMenu(hMenu, TPM_BOTTOMALIGN | TPM_LEFTALIGN, pt.x, pt.y, 0, hwnd, NULL);
            DestroyMenu(hMenu);
        }
        return 0;
    }

    case WM_COMMAND: {
        // Ejecutar la acción seleccionada en el menú del System Tray
        if (LOWORD(wParam) == IDM_EXIT) {
            PostQuitMessage(0);
        }
        return 0;
    }

    case WM_DESTROY:
        PostQuitMessage(0);
        return 0;
    }
    return DefWindowProc(hwnd, uMsg, wParam, lParam);
}

// 1. Punto de entrada de la aplicación
int WINAPI wWinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, PWSTR pCmdLine, int nCmdShow) {
    // Inicializar COM en modo multihilo (Requisito AC-1)
    HRESULT hr = RoInitialize(RO_INIT_MULTITHREADED);

    // Registrar la clase de ventana oculta (para manejar los mensajes del sistema)
    WNDCLASS wc = { 0 };
    wc.lpfnWndProc = WindowProc;
    wc.hInstance = hInstance;
    wc.lpszClassName = L"DyslexiaReaderHiddenWin";
    RegisterClass(&wc);

    HWND hwndMessage = CreateWindowEx(0, wc.lpszClassName, L"MessageWindow", 0, 0, 0, 0, 0, HWND_MESSAGE, NULL, hInstance, NULL);

    // 2. Configurar el System Tray (Icono al lado del reloj)
    NOTIFYICONDATA nid = { 0 };
    nid.cbSize = sizeof(NOTIFYICONDATA);
    nid.hWnd = hwndMessage;
    nid.uID = ID_TRAY_ICON;
    nid.uFlags = NIF_MESSAGE | NIF_ICON | NIF_TIP;
    nid.uCallbackMessage = WM_TRAYICON;
    nid.hIcon = LoadIcon(NULL, IDI_APPLICATION); // Icono por defecto
    wcscpy_s(nid.szTip, L"Dyslexia Reader");
    Shell_NotifyIcon(NIM_ADD, &nid);

    // 3. Registrar Atajos Globales (Hotkeys)
    // MOD_CONTROL = Ctrl, MOD_SHIFT = Shift
    RegisterHotKey(hwndMessage, HOTKEY_CAPTURE, MOD_CONTROL | MOD_SHIFT, VK_SPACE);
    RegisterHotKey(hwndMessage, HOTKEY_CLIPBOARD, MOD_CONTROL | MOD_SHIFT, 0x56); // 0x56 es la tecla 'V'

    // 4. Crear las ventanas (Floating y Focus - Ocultas por defecto)
    // Floating: WS_EX_TOPMOST asegura que siempre esté arriba de otras apps
    hwndFloating = CreateWindowEx(WS_EX_TOPMOST, wc.lpszClassName, L"Floating Reader", WS_OVERLAPPEDWINDOW, 100, 100, 400, 300, NULL, NULL, hInstance, NULL);

    // Focus: Pantalla completa, sin bordes (WS_POPUP)
    int screenWidth = GetSystemMetrics(SM_CXSCREEN);
    int screenHeight = GetSystemMetrics(SM_CYSCREEN);
    hwndFocus = CreateWindowEx(WS_EX_TOPMOST, wc.lpszClassName, L"Focus Reader", WS_POPUP, 0, 0, screenWidth, screenHeight, NULL, NULL, hInstance, NULL);

    // Cargar configuraciones (Requisito 5)
    ManageSettings(false);

    // Bucle de mensajes (Mantiene la aplicación viva esperando eventos)
    MSG msg = { 0 };
    while (GetMessage(&msg, NULL, 0, 0)) {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }

    // Limpieza al cerrar
    Shell_NotifyIcon(NIM_DELETE, &nid);
    RoUninitialize();
    return 0;
}