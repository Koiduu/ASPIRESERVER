package com.aspireserver.duelbot.fsm;

import com.aspireserver.duelbot.fsm.states.ClutchState;
import com.aspireserver.duelbot.fsm.states.EngageState;
import com.aspireserver.duelbot.fsm.states.IdleState;
import com.aspireserver.duelbot.fsm.states.PanicState;
import com.aspireserver.duelbot.npc.CombatTrait;

import java.util.EnumMap;
import java.util.Map;

/** Owns state instances and applies the transition rules from Section 6. */
public final class StateMachine {

    private final Map<BotState, State> states = new EnumMap<>(BotState.class);
    private State current;
    private BotState preClutch = BotState.ENGAGE;

    public StateMachine() {
        register(new IdleState());
        register(new EngageState());
        register(new PanicState());
        register(new ClutchState());
        current = states.get(BotState.IDLE);
    }

    private void register(State s) {
        states.put(s.id(), s);
    }

    public BotState currentId() {
        return current.id();
    }

    public BotState preClutchState() {
        return preClutch;
    }

    public void tick(CombatTrait trait) {
        BotState next = evaluate(trait);
        if (next != current.id()) {
            if (next == BotState.CLUTCH && current.id() != BotState.CLUTCH) {
                preClutch = current.id();
            }
            transitionTo(trait, next);
        }
        current.tick(trait);
    }

    private void transitionTo(CombatTrait trait, BotState next) {
        current.onExit(trait);
        current = states.get(next);
        current.onEnter(trait);
    }

    private BotState evaluate(CombatTrait trait) {
        BotState id = current.id();

        // Clutch preempts everything — falling to void is immediately fatal.
        if (id != BotState.CLUTCH && trait.shouldClutch()) {
            return BotState.CLUTCH;
        }

        switch (id) {
            case IDLE -> {
                if (trait.hasTarget() && trait.targetInAggroRange()) return BotState.ENGAGE;
                return BotState.IDLE;
            }
            case ENGAGE -> {
                if (!trait.hasTarget() || trait.targetOutOfDisengageRange()) return BotState.IDLE;
                if (trait.healthRatio() <= trait.panicHealthRatio() && trait.underPressure()) return BotState.PANIC;
                return BotState.ENGAGE;
            }
            case PANIC -> {
                if (!trait.hasTarget()) return BotState.IDLE;
                if (trait.healthRatio() >= trait.reengageHealthRatio() && !trait.underPressure()) return BotState.ENGAGE;
                return BotState.PANIC;
            }
            case CLUTCH -> {
                if (!trait.shouldClutch()) {
                    return trait.hasTarget() ? preClutch : BotState.IDLE;
                }
                return BotState.CLUTCH;
            }
        }
        return id;
    }
}
