package com.timeflow.config;

/**
 * PerfilSession — estado global do usuário logado.
 *
 * Singleton simples para guardar dados do perfil durante a sessão do app.
 * Acesse de qualquer controller sem passar parâmetros.
 *
 * Uso:
 *   // No LoginController após login:
 *   PerfilSession.set(result.perfilId(), result.empresaId(), result.nome(), result.nivel(), result.token());
 *
 *   // Em qualquer lugar do app:
 *   String id = PerfilSession.getId();
 *   boolean isGestor = PerfilSession.isGestor();
 */
public final class PerfilSession {

    private PerfilSession() {}

    private static String perfilId  = null;
    private static String empresaId = null;
    private static String nome      = null;
    private static String nivel     = null;
    private static String token     = null;
    private static boolean logado   = false;

    // ── Setters ───────────────────────────────────────────────────────

    public static void set(String perfilId, String empresaId, String nome, String nivel, String token) {
        PerfilSession.perfilId  = perfilId;
        PerfilSession.empresaId = empresaId;
        PerfilSession.nome      = nome;
        PerfilSession.nivel     = nivel;
        PerfilSession.token     = token;
        PerfilSession.logado    = true;
    }

    public static void clear() {
        perfilId  = null;
        empresaId = null;
        nome      = null;
        nivel     = null;
        token     = null;
        logado    = false;
    }

    // ── Getters ───────────────────────────────────────────────────────

    public static String  getId()        { return perfilId;  }
    public static String  getEmpresaId() { return empresaId; }
    public static String  getNome()      { return nome;      }
    public static String  getNivel()     { return nivel;     }
    public static String  getToken()     { return token;     }
    public static boolean isLogado()     { return logado;    }
    public static boolean isGestor()     { return "gestor".equalsIgnoreCase(nivel); }
    public static boolean isLider()      { return "lider".equalsIgnoreCase(nivel);  }
}