package ch.alya.clippings;

import net.minecraft.server.ItemBlock;

/**
 * Replacement item for block 31 (tall grass).
 *
 * Vanilla registers block 31 as a plain ItemBlock: no subtypes, and
 * filterData() always returns 0. Grass, fern and shrub items would merge into
 * one stack and always place as the dead shrub (31:0).
 *
 * This works like ItemLeaves: the item has subtypes, so stacks stay separated
 * by data value, and the data value is passed on to the placed block.
 */
public class ItemTallGrass extends ItemBlock {

    public ItemTallGrass(int i) {
        super(i);
        this.d(0);      // no durability
        this.a(true);   // has subtypes: stacks keep their data value
    }

    public int filterData(int i) {
        return i;       // 0 = shrub, 1 = tall grass, 2 = fern
    }
}
