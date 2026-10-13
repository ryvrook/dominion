package dev.ryvrook.dominion.combat;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.dominion.DominionContext;
import dev.ryvrook.dominion.DominionModule;
import dev.ryvrook.dominion.service.RulesService;

import java.util.function.Supplier;
import java.util.logging.Level;

/** Protection and PvP rules plus the ECS systems that enforce them. */
public final class CombatModule implements DominionModule {
    private RulesServiceImpl rules;

    public CombatModule() {}

    @Override
    public String name() {
        return "combat";
    }

    @Override
    public void init(DominionContext ctx) {
        rules = new RulesServiceImpl(ctx);
        ctx.register(RulesService.class, rules);
    }

    @Override
    public void start(DominionContext ctx) {
        Denials denials = new Denials();
        ComponentRegistryProxy<EntityStore> registry = ctx.plugin().getEntityStoreRegistry();
        register(ctx, registry, () -> new ProtectionSystems.BreakGuard(rules, denials));
        register(ctx, registry, () -> new ProtectionSystems.PlaceGuard(rules, denials));
        register(ctx, registry, () -> new ProtectionSystems.DamageBlockGuard(rules, denials));
        register(ctx, registry, () -> new ProtectionSystems.UseBlockGuard(rules, denials));
        register(ctx, registry, () -> new ProtectionSystems.UseEntityGuard(rules, denials));
        register(ctx, registry, () -> new PvpFilterSystem(ctx, rules, denials));
        register(ctx, registry, () -> new DeathTrackingSystem(ctx));
        register(ctx, registry, () -> new HomeRespawnSystem(ctx));
    }

    /** One failing system must not stop the others from loading. */
    private static void register(DominionContext ctx, ComponentRegistryProxy<EntityStore> registry,
                                 Supplier<ISystem<EntityStore>> system) {
        try {
            registry.registerSystem(system.get());
        } catch (RuntimeException e) {
            ctx.logger().at(Level.SEVERE).withCause(e).log("Dominion combat system failed to register");
        }
    }
}
