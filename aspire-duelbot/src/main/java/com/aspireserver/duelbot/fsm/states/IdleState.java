package com.aspireserver.duelbot.fsm.states;

import com.aspireserver.duelbot.fsm.BotState;
import com.aspireserver.duelbot.fsm.State;
import com.aspireserver.duelbot.npc.CombatTrait;

/** No target in range: hand movement back to the Navigator and stand down. */
public final class IdleState implements State {

    @Override
    public BotState id() {
        return BotState.IDLE;
    }

    @Override
    public void onEnter(CombatTrait trait) {
        trait.releaseNavigatorControl();
    }

    @Override
    public void tick(CombatTrait trait) {
        trait.idleTick();
    }
}
