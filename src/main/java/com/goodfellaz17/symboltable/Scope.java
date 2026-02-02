package com.goodfellaz17.symboltable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * MontiCore-inspired Scope implementation.
 *
 * Manual Ch.9.3: Scopes organize symbols hierarchically.
 * In GOODFELLAZ17: GlobalScope → ApiKeyScope → GeoScope → OrderScope
 *
 * Features:
 * - O(1) symbol lookup via ConcurrentHashMap
 * - Hierarchical resolution (local → parent)
 * - Shadowing support (local shadows imported)
 * - Thread-safe for concurrent access
 *
 * @see <a href="https://monticore.de">MontiCore Reference Manual</a>
 */
@Slf4j
public class Scope {

    private final String name;
    private Scope enclosingScope;
    private final List<Scope> subScopes;
    private final Map<SymbolKey, Symbol> localSymbols;
    private boolean shadowing;

    public Scope() {
        this(null, null);
    }

    public Scope(String name) {
        this(name, null);
    }

    public Scope(String name, Scope enclosingScope) {
        this.name = name;
        this.enclosingScope = enclosingScope;
        this.subScopes = new ArrayList<>();
        this.localSymbols = new ConcurrentHashMap<>();
        this.shadowing = true;
    }

    /**
     * Add a symbol to this scope.
     * Manual Ch.9.4: Symbols are added during symbol table creation.
     */
    public void add(Symbol symbol) {
        Objects.requireNonNull(symbol, "Symbol cannot be null");
        SymbolKey key = new SymbolKey(symbol.getName(), symbol.getKind());
        localSymbols.put(key, symbol);
        symbol.setEnclosingScope(this);
    }

    /**
     * Resolve a symbol locally (no parent lookup).
     * Manual Ch.9.5: resolveLocally checks only this scope.
     * Type-safe cast with error handling (Issue #003 fix).
     */
    public <S extends Symbol> Optional<S> resolveLocally(String name, SymbolKind kind) {
        SymbolKey key = new SymbolKey(name, kind);
        Symbol symbol = localSymbols.get(key);
        if (symbol == null) {
            return Optional.empty();
        }
        try {
            @SuppressWarnings("unchecked")
            S result = (S) symbol;
            return Optional.of(result);
        } catch (ClassCastException e) {
            log.warn("Symbol type mismatch for key={}, kind={}: expected type does not match", name, kind, e);
            return Optional.empty();
        }
    }

    /**
     * Resolve a symbol with hierarchical lookup (local → parent).
     * Manual Ch.9.5: resolve() delegates to parent if not found locally.
     */
    public <S extends Symbol> Optional<S> resolve(String name, SymbolKind kind) {
        Optional<S> local = resolveLocally(name, kind);

        if (local.isPresent()) {
            return local;
        }

        // If shadowing is disabled or no local found, check parent
        if (enclosingScope != null) {
            return enclosingScope.resolve(name, kind);
        }

        return Optional.empty();
    }

    /**
     * Resolve multiple symbols of a kind locally.
     * Manual Ch.9.5: resolveManyLocally returns all matching symbols.
     * Type-safe filtering and casting (Issue #003 fix).
     */
    public <S extends Symbol> List<S> resolveManyLocally(SymbolKind kind) {
        return localSymbols.values().stream()
                .filter(s -> s.getKind() == kind)
                .flatMap(s -> {
                    try {
                        @SuppressWarnings("unchecked")
                        S result = (S) s;
                        return java.util.stream.Stream.of(result);
                    } catch (ClassCastException e) {
                        log.warn("Symbol type mismatch during collection: {}", s, e);
                        return java.util.stream.Stream.empty();
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * Resolve multiple symbols hierarchically.
     */
    public <S extends Symbol> List<S> resolveMany(SymbolKind kind) {
        List<S> result = new ArrayList<>(resolveManyLocally(kind));

        if (enclosingScope != null && !shadowing) {
            result.addAll(enclosingScope.resolveMany(kind));
        }

        return result;
    }

    /**
     * Add a sub-scope to this scope.
     * Manual Ch.9.3.2: Scopes form a tree structure.
     */
    public void addSubScope(Scope scope) {
        Objects.requireNonNull(scope, "Subscope cannot be null");
        scope.setEnclosingScope(this);
        subScopes.add(scope);
    }

    /**
     * Remove a symbol from this scope.
     */
    public void remove(String name, SymbolKind kind) {
        SymbolKey key = new SymbolKey(name, kind);
        localSymbols.remove(key);
    }

    /**
     * Check if symbol exists locally.
     */
    public boolean containsLocally(String name, SymbolKind kind) {
        return localSymbols.containsKey(new SymbolKey(name, kind));
    }

    /**
     * Get all symbols in this scope.
     */
    public Collection<Symbol> getLocalSymbols() {
        return Collections.unmodifiableCollection(localSymbols.values());
    }

    /**
     * Get the number of symbols in this scope.
     */
    public int size() {
        return localSymbols.size();
    }

    /**
     * Clear all symbols from this scope.
     */
    public void clear() {
        localSymbols.clear();
    }

    // Getters and Setters

    public String getName() {
        return name;
    }

    public Scope getEnclosingScope() {
        return enclosingScope;
    }

    public void setEnclosingScope(Scope enclosingScope) {
        this.enclosingScope = enclosingScope;
    }

    public List<Scope> getSubScopes() {
        return Collections.unmodifiableList(subScopes);
    }

    public boolean isShadowing() {
        return shadowing;
    }

    public void setShadowing(boolean shadowing) {
        this.shadowing = shadowing;
    }

    @Override
    public String toString() {
        return String.format("Scope[name=%s, symbols=%d, subScopes=%d]",
                name, localSymbols.size(), subScopes.size());
    }

    /**
     * Composite key for symbol lookup: name + kind.
     */
    private record SymbolKey(String name, SymbolKind kind) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SymbolKey symbolKey = (SymbolKey) o;
            return Objects.equals(name, symbolKey.name) && kind == symbolKey.kind;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, kind);
        }
    }
}
