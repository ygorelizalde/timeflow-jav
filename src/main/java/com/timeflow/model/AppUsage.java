package com.timeflow.model;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AppUsage — modelo de dados para uso de um aplicativo numa sessão.
 * Thread-safe para uso do AgenteInvisivel.
 */
public class AppUsage {

    private final String nome;
    private volatile String   categoria  = "outros";
    private volatile double   tempoSegundos = 0;
    private final AtomicInteger teclas   = new AtomicInteger(0);
    private final AtomicInteger cliques  = new AtomicInteger(0);
    private final AtomicInteger switches = new AtomicInteger(0);
    private final AtomicLong    ativo    = new AtomicLong(0);
    private final AtomicLong    idle     = new AtomicLong(0);

    public AppUsage(String nome) { this.nome = nome; }

    // ── Mutações ──────────────────────────────────────────────────────
    public synchronized void addTempo(double seg) { tempoSegundos += seg; }
    public void incrementTeclas()   { teclas.incrementAndGet(); }
    public void incrementCliques()  { cliques.incrementAndGet(); }
    public void incrementSwitches() { switches.incrementAndGet(); }
    public void incrementAtivo()    { ativo.incrementAndGet(); }
    public void incrementIdle()     { idle.incrementAndGet(); }
    public void setCategoria(String c) { this.categoria = c; }

    // ── Leituras ──────────────────────────────────────────────────────
    public String  getNome()          { return nome; }
    public String  getCategoria()     { return categoria; }
    public synchronized double getTempoSegundos() { return tempoSegundos; }
    public int     getTeclas()        { return teclas.get(); }
    public int     getCliques()       { return cliques.get(); }
    public int     getSwitches()      { return switches.get(); }
    public long    getAtivo()         { return ativo.get(); }
    public long    getIdle()          { return idle.get(); }
}
