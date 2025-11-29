import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

enum OrderStatus { NEW, PROCESSING, IN_DELIVERY, COMPLETED, CANCELLED }
enum PaymentStatus { PENDING, PROCESSED, REFUNDED, FAILED }
enum DeliveryStatus { PENDING, SHIPPED, IN_TRANSIT, DELIVERED }

abstract class User {
    String id;
    String name;
    String email;
    String address;
    String phone;
    String role;
    public User(String id, String name, String email){
        this.id=id; this.name=name; this.email=email;
    }
    public void register(){ System.out.println(name + " registered.");}
    public boolean login(){ System.out.println(name + " logged in."); return true; }
    public void updateProfile(){ System.out.println(name + " profile updated."); }
}

class Customer extends User {
    List<Order> orderHistory = new ArrayList<>();
    int loyaltyPoints = 0;
    Cart cart = new Cart();
    public Customer(String id, String name, String email){ super(id,name,email); this.role="CUSTOMER"; }
    public void addToCart(Product p, int qty){ cart.addItem(p, qty); }
    public Order checkout(){
        Order o = cart.createOrder(this);
        orderHistory.add(o);
        cart = new Cart();
        return o;
    }
    public void leaveReview(Product p, Review r){ p.reviews.add(r); }
}

class Admin extends User {
    List<AdminAction> log = new ArrayList<>();
    public Admin(String id, String name, String email){ super(id,name,email); this.role="ADMIN"; }
    public void createProduct(Product p){ System.out.println("Product created: " + p.name); log.add(new AdminAction(this.id, "CREATE_PRODUCT", p.name)); }
    public void logAction(String desc){ log.add(new AdminAction(this.id, "ACTION", desc)); }
}

class AdminAction {
    String adminId; String type; String description; LocalDateTime timestamp;
    public AdminAction(String adminId, String type, String desc){
        this.adminId=adminId; this.type=type; this.description=desc; this.timestamp=LocalDateTime.now();
    }
}

class Product {
    String id; String name; String description; BigDecimal price;
    int stockQuantity;
    Category category;
    BigDecimal discount = BigDecimal.ZERO;
    List<Review> reviews = new ArrayList<>();
    public Product(String id, String name, BigDecimal price, int stock){
        this.id=id; this.name=name; this.price=price; this.stockQuantity=stock;
    }
    public BigDecimal getPrice(){ return price.subtract(discount); }
    public void applyDiscount(BigDecimal d){ discount = d; }
}

class Category { String id; String name; Category parent; public Category(String id,String name){this.id=id;this.name=name;} }

class Cart {
    Map<Product,Integer> items = new HashMap<>();
    PromoCode promo;
    public void addItem(Product p, int qty){ items.put(p, items.getOrDefault(p,0)+qty); }
    public void removeItem(Product p){ items.remove(p); }
    public BigDecimal calculateTotal(){
        BigDecimal total = BigDecimal.ZERO;
        for(Map.Entry<Product,Integer> e: items.entrySet()){
            total = total.add(e.getKey().getPrice().multiply(BigDecimal.valueOf(e.getValue())));
        }
        if(promo!=null && promo.isValid()){
            total = promo.apply(total);
        }
        return total;
    }
    public Order createOrder(Customer c){
        Order o = new Order(UUID.randomUUID().toString(), c, new ArrayList<>());
        for(Map.Entry<Product,Integer> e: items.entrySet()){
            o.items.add(new OrderItem(e.getKey(), e.getValue(), e.getKey().getPrice()));
        }
        o.totalAmount = calculateTotal();
        o.status = OrderStatus.NEW;
        return o;
    }
}

class PromoCode {
    String code; int discountPercent; Date validUntil;
    public PromoCode(String code, int percent){ this.code=code; this.discountPercent=percent; }
    public boolean isValid(){ return true; }
    public BigDecimal apply(BigDecimal amount){ return amount.multiply(BigDecimal.valueOf(100-discountPercent)).divide(BigDecimal.valueOf(100)); }
}

class Order {
    String id; LocalDateTime createdAt; OrderStatus status; Customer customer;
    List<OrderItem> items; BigDecimal totalAmount;
    Payment payment; Delivery delivery;
    public Order(String id, Customer c, List<OrderItem> items){ this.id=id; this.customer=c; this.items=items; this.createdAt=LocalDateTime.now(); }
    public void place(){ System.out.println("Order placed: " + id); this.status=OrderStatus.PROCESSING; }
    public void cancel(){ this.status=OrderStatus.CANCELLED; System.out.println("Order cancelled: " + id); }
    public boolean pay(Payment p){
        this.payment = p;
        boolean ok = p.process();
        if(ok) { this.status = OrderStatus.IN_DELIVERY; System.out.println("Payment success for order " + id); }
        else System.out.println("Payment failed for order " + id);
        return ok;
    }
}

class OrderItem {
    Product product; int quantity; BigDecimal priceAtPurchase;
    public OrderItem(Product p, int q, BigDecimal price){ this.product=p; this.quantity=q; this.priceAtPurchase=price; }
}

abstract class Payment {
    String id; BigDecimal amount; PaymentStatus status;
    public Payment(BigDecimal amount){ this.amount=amount; this.id=UUID.randomUUID().toString(); this.status=PaymentStatus.PENDING; }
    public abstract boolean process();
    public abstract boolean refund();
}

class CardPayment extends Payment {
    String cardMasked;
    public CardPayment(BigDecimal amount, String cardMasked){ super(amount); this.cardMasked=cardMasked; }
    public boolean process(){ System.out.println("Processing card payment " + id); status = PaymentStatus.PROCESSED; return true; }
    public boolean refund(){ status = PaymentStatus.REFUNDED; return true; }
}

class Delivery {
    String id; String address; DeliveryStatus status; Courier courier; String trackingNumber;
    public Delivery(String addr){ this.id=UUID.randomUUID().toString(); this.address=addr; this.status=DeliveryStatus.PENDING; }
    public void ship(Courier c){ this.courier=c; this.status=DeliveryStatus.SHIPPED; System.out.println("Shipped by " + c.name); }
    public void complete(){ this.status=DeliveryStatus.DELIVERED; System.out.println("Delivery complete " + id); }
}

class Courier {
    String id; String name; String phone;
    public Courier(String id, String name){ this.id=id; this.name=name; }
    public void acceptDelivery(Delivery d){ System.out.println(name + " accepted delivery " + d.id); d.ship(this); }
}

class Warehouse {
    String id; String location; Map<Product,Integer> inventory = new HashMap<>();
    public Warehouse(String id, String location){ this.id=id; this.location=location; }
    public int getStock(Product p){ return inventory.getOrDefault(p,0); }
    public void addStock(Product p, int q){ inventory.put(p, getStock(p)+q); }
    public boolean reserveStock(Product p, int q){
        int available = getStock(p);
        if(available >= q){ inventory.put(p, available - q); return true; }
        return false;
    }
}

class Review {
    String id; Product product; Customer customer; int rating; String text; LocalDateTime createdAt;
    public Review(Product p, Customer c, int rating, String text){ this.id=UUID.randomUUID().toString(); this.product=p; this.customer=c; this.rating=rating; this.text=text; this.createdAt=LocalDateTime.now(); }
}

interface RouteOptimizer {
    String calculateRoute(List<Delivery> deliveries);
}

class SimpleRouteOptimizer implements RouteOptimizer {
    public String calculateRoute(List<Delivery> deliveries){
        // stub: return naive route
        return "Route for " + deliveries.size() + " deliveries (naive)";
    }
}

public class Main {
    public static void main(String[] args) {

        Customer alice = new Customer("c1","Alice","alice@example.com");
        Admin admin = new Admin("a1","Bob","bob@shop.com");

        Product p1 = new Product("p1","Widget", new BigDecimal("10.00"), 100);
        Product p2 = new Product("p2","Gadget", new BigDecimal("20.00"), 50);

        admin.createProduct(p1);
        admin.createProduct(p2);

        alice.addToCart(p1,2);
        alice.addToCart(p2,1);
        Order order = alice.checkout();
        order.place();

        Payment pay = new CardPayment(order.totalAmount != null ? order.totalAmount : new BigDecimal("40.00"), "**** **** **** 1234");
        order.pay(pay);

        Delivery d = new Delivery("123 Main St");
        Courier courier = new Courier("cour1","Dmitry");
        courier.acceptDelivery(d);
        d.complete();

        System.out.println("Demo finished.");
    }
}
