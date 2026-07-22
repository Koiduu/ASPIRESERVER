package com.aspireserver.duelbot.fsm.states;

import com.aspireserver.duelbot.fsm.BotState;
import com.aspireserver.duelbot.fsm.State;
import com.aspireserver.duelbot.npc.CombatTrait;

/** Active combat: aim, orbit-strafe / close distance, swing with crit-jump + W-tap. */
public final class EngageState implements State {

    @Override
    public BotState id() {
        return BotState.ENGAGE;
    }

    @Override
    public void onEnter(CombatTrait trait) {
        trait.onEngageStart();
    }

    @Override
    public void tick(CombatTrait trait) {
        trait.faceTarget();
        trait.combatMovement();
        trait.tickCombatSwing();
        trait.tryBlockTap();
    }
}
