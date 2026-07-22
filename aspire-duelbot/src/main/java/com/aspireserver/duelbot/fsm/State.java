package com.aspireserver.duelbot.fsm;

import com.aspireserver.duelbot.npc.CombatTrait;

/** A single FSM state. Behaviour runs in {@link #tick(CombatTrait)} each Citizens tick. */
public interface State {

    BotState id();

    default void onEnter(CombatTrait trait) {
    }

    void tick(CombatTrait trait);

    default void onExit(CombatTrait trait) {
    }
}
