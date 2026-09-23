package haven.res.ui.barterbox;

import nurgling.i18n.L10n;

final class BarterText {
    private BarterText() {}
    static boolean russian(String language) {return("ru".equals(language));}
    private static boolean ru() {return(russian(L10n.getLanguage()));}
    static String text(String key) {
        boolean r = ru();
        if(key.equals("receive")) return(r ? "ПОЛУЧАЕТЕ" : "YOU RECEIVE");
        if(key.equals("pay")) return(r ? "ОПЛАЧИВАЕТЕ" : "YOU PAY");
        if(key.equals("choose_amount")) return(r ? "Сделки / цена" : "Deals / cost");
        if(key.equals("buy")) return(r ? "Купить" : "Buy");
        if(key.equals("sold_out")) return(r ? "Распродано" : "Sold out");
        if(key.equals("stock_loading")) return(r ? "Остаток загружается" : "Stock loading");
        if(key.equals("offer_loading")) return(r ? "Товар загружается" : "Item loading");
        if(key.equals("payment_loading")) return(r ? "Цена загружается" : "Payment loading");
        if(key.equals("loading")) return(r ? "Загрузка…" : "Loading…");
        if(key.equals("quality_unknown")) return(r ? "Качество: неизвестно" : "Quality: unknown");
        if(key.equals("invalid_number")) return(r ? "Введите целое число" : "Enter a whole number");
        if(key.equals("quantity_positive")) return(r ? "Количество должно быть больше нуля" : "Quantity must be greater than zero");
        if(key.equals("seller_controls")) return(r ? "УПРАВЛЕНИЕ ПРОДАЖЕЙ" : "SELLER CONTROLS");
        if(key.equals("seller_short")) return(r ? "Продажа:" : "Selling:");
        if(key.equals("connect_stock")) return(r ? "Подключить товар" : "Connect goods");
        if(key.equals("connect_payment")) return(r ? "Подключить оплату" : "Connect payment");
        if(key.equals("change_offer")) return(r ? "Изменить товар" : "Change offer");
        if(key.equals("price_amount")) return(r ? "Цена за сделку" : "Price per deal");
        if(key.equals("min_quality")) return(r ? "Мин. качество" : "Minimum quality");
        if(key.equals("seller_price")) return(r ? "Цена" : "Price");
        if(key.equals("seller_quality")) return(r ? "Мин. Q" : "Min Q");
        return(key);
    }
    static String tip(String key) {
        boolean r = ru();
        if(key.equals("quantity")) return(r ? "Сколько сделок купить (1–500)." : "How many deals to buy (1–500).");
        if(key.equals("quick_buy")) return(r ? "Купить указанное количество сразу." : "Buy the shown amount immediately.");
        if(key.equals("custom_buy")) return(r ? "Купить введённое количество." : "Buy the entered quantity.");
        if(key.equals("connect_stock")) return(r ? "Подключить контейнер с товаром." : "Connect the goods container.");
        if(key.equals("connect_payment")) return(r ? "Подключить контейнер для оплаты." : "Connect the payment container.");
        if(key.equals("change_offer")) return(r ? "Сменить выставленный товар." : "Change the offered item.");
        if(key.equals("price_amount")) return(r ? "Сколько единиц оплаты требуется за сделку." : "Payment units required per deal.");
        if(key.equals("min_quality")) return(r ? "Минимальное качество оплаты; 0 — любое." : "Minimum payment quality; 0 accepts any.");
        return("");
    }
    static String stock(int stock) {return(ru() ? "В наличии: " + stock : "In stock: " + stock);}
    static String lotSize(int amount) {return(ru() ? "Лот: " + amount : "Lot: " + amount);}
    static String costUnit(int amount) {return(ru() ? "Цена: " + amount + " за сделку" : "Cost: " + amount + " per deal");}
    static String minimumQuality(int quality) {return(ru() ? "Мин. качество: " + ((quality > 0) ? quality + "+" : "любое") : "Minimum quality: " + ((quality > 0) ? quality + "+" : "any"));}
    static String quality(double quality) {return(ru() ? String.format(java.util.Locale.ROOT, "Качество: %.1f", quality) : String.format(java.util.Locale.ROOT, "Quality: %.1f", quality));}
    static String total(int quantity, int unitPrice) {return(ru() ? "Сделок: " + quantity + " • Итого: " + ((long)quantity * unitPrice) : "Deals: " + quantity + " • Total: " + ((long)quantity * unitPrice));}
    static String total(int quantity, int unitPrice, int lotSize) {
        long received = (long)quantity * lotSize;
        long total = (long)quantity * unitPrice;
        return(ru() ? "Сделок: " + quantity + " • Получите: " + received + " • Итого: " + total : "Deals: " + quantity + " • Receive: " + received + " • Total: " + total);
    }
    static String buyCount(int quantity) {return(ru() ? "Купить " + quantity : "Buy " + quantity);}
    static String costOnly(int quantity, int unitPrice) {return(ru() ? "Цена: " + ((long)quantity * unitPrice) : "Cost: " + ((long)quantity * unitPrice));}
    static String customBuy(int quantity, int unitPrice) {return(ru() ? "Купить " + quantity + " (" + ((long)quantity * unitPrice) + ")" : "Buy " + quantity + " (" + ((long)quantity * unitPrice) + ")");}
    static String quickBuy(int quantity, int unitPrice) {return(customBuy(quantity, unitPrice));}
    static String quickLabel(int quantity) {return(Integer.toString(quantity));}
    static String compactQuick(int quantity, int unitPrice) {return(buyCount(quantity) + " · " + ((long)quantity * unitPrice));}
    static String compactTotal(int quantity, int unitPrice, int lotSize) {
        String cost = (ru() ? "Итого: " : "Total: ") + ((long)quantity * unitPrice);
        return lotSize > 1 ? cost + "\n" + (ru() ? "Получите: " : "Receive: ") + ((long)quantity * lotSize) : cost;
    }
    static String quickTip(int quantity, int unitPrice) {return(ru() ? "Купить " + quantity + "; цена: " + ((long)quantity * unitPrice) : "Buy " + quantity + "; cost: " + ((long)quantity * unitPrice));}
    static String maximum(int max) {return(ru() ? "Максимум: " + max : "Maximum: " + max);}
    static String stockLimit(int stock) {return(ru() ? "Доступно только: " + stock : "Only " + stock + " available");}
}
