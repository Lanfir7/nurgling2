package haven.res.ui.barterbox;

import java.util.LinkedHashSet;

final class BarterPurchase {
    private BarterPurchase() {}
    static int clamp(int quantity, int stock, int max) {return(Math.min(quantity, Math.min(stock, max)));}
    static boolean canBuy(int quantity, boolean stockKnown, int stock, int max, boolean hasOffer, boolean hasPrice, int unitPrice) {
        return(stockKnown && (stock > 0) && hasOffer && hasPrice && unitPrice > 0 && quantity > 0 && quantity <= max);
    }
    static int emittedBuyCount(int quantity, boolean stockKnown, int stock, int max, boolean hasOffer, boolean hasPrice, int unitPrice) {
        return(canBuy(quantity, stockKnown, stock, max, hasOffer, hasPrice, unitPrice) ? clamp(quantity, stock, max) : 0);
    }
    static boolean priceSpriteReady(boolean hasPrice, boolean alreadyReady, boolean resolvedNow) {
        return(hasPrice && (alreadyReady || resolvedNow));
    }
    static int[] quickCounts(boolean stockKnown, int stock) {
        int[] out = {0, 0, 0};
        if(!stockKnown || stock < 1) return(out);
        LinkedHashSet<Integer> counts = new LinkedHashSet<>();
        for(int requested : new int[] {1, 5, 20}) counts.add(Math.min(requested, stock));
        int i = 0; for(int count : counts) out[i++] = count;
        return(out);
    }
    static QuantityState quantityState(Integer quantity, boolean stockKnown, int stock, int max, int unitPrice) {
        if(!stockKnown) return(new QuantityState(false, 0, BarterText.text("stock_loading")));
        if(stock < 1) return(new QuantityState(false, 0, BarterText.text("sold_out")));
        if(quantity == null) return(new QuantityState(false, 0, BarterText.text("invalid_number")));
        if(quantity < 1) return(new QuantityState(false, 0, BarterText.text("quantity_positive")));
        if(quantity > max) return(new QuantityState(false, quantity, BarterText.maximum(max)));
        if(quantity > stock) quantity = stock;
        if(unitPrice < 1) return(new QuantityState(false, quantity, BarterText.text("payment_loading")));
        return(new QuantityState(true, quantity, BarterText.total(quantity, unitPrice)));
    }
    static QuantityState quantityState(Integer quantity, boolean stockKnown, int stock, int max, int unitPrice, int lotSize) {
        QuantityState state = quantityState(quantity, stockKnown, stock, max, unitPrice);
        return(state.valid ? new QuantityState(true, state.quantity, BarterText.total(state.quantity, unitPrice, lotSize)) : state);
    }
}

final class QuantityState {
    final boolean valid;
    final int quantity;
    final String message;
    QuantityState(boolean valid, int quantity, String message) {this.valid = valid; this.quantity = quantity; this.message = message;}
}
