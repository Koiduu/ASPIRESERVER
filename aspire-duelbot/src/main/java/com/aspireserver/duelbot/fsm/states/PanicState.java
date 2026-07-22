package com.aspireserver.duelbot.fsm.states;

import com.aspireserver.duelbot.fsm.BotState;
import com.aspireserver.duelbot.fsm.State;
import com.aspireserver.duelbot.npc.CombatTrait;

/** Low health under pressure: retreat, wall off line-of-sight, and eat a golden apple. */
public final class PanicState implements State {

    @Override
    public BotState id() {
        return BotState.PANIC;
    }

    @Override
    public void onEnter(CombatTrait trait) {
        trait.beginBoxIn();
    }

    @Override
    public void tick(CombatTrait trait) {
        trait.faceTarget();
        trait.tickBoxIn();
    }
}
