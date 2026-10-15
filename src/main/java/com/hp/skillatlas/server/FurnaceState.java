package com.hp.skillatlas.server;

import java.util.UUID;

public interface FurnaceState {
    UUID skillAtlas$owner();
    void skillAtlas$owner(UUID owner);
    double skillAtlas$carry();
    void skillAtlas$carry(double carry);
    int skillAtlas$progress();
    void skillAtlas$progress(int progress);
    int skillAtlas$totalTime();
    int skillAtlas$burnTime();
    void skillAtlas$burnTime(int time);
}
