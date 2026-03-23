package ax.it.inventory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.Scanner;

public class InventoryApp {

    private static final String URL = "jdbc:mysql://localhost:3306/inventory_db";
    private static final String USER = "root";
    private static final String PASS = "PASSWORD"; // Put your password from mysql here pls
    private static final int LOW_STOCK_LIMIT = 5;

    public static void main(String[] args) {
        try (Scanner sc = new Scanner(System.in);
             Connection conn = DriverManager.getConnection(URL, USER, PASS)) {

            while (true) {
                printMenu();
                int choice = readInt(sc, "Choose an option: ");

                switch (choice) {
                    case 1 -> addItem(conn, sc);
                    case 2 -> viewItems(conn);
                    case 3 -> createOrder(conn, sc);
                    case 4 -> viewOrders(conn);
                    case 5 -> updateItem(conn, sc);
                    case 6 -> deleteItem(conn, sc);
                    case 7 -> lowStockReport(conn);
                    case 8 -> inventoryValueReport(conn);
                    case 0 -> {
                        System.out.println("Bye.");
                        return;
                    }
                    default -> System.out.println("Invalid choice.");
                }
            }

        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
    }

    private static void printMenu() {
        System.out.println("\n=== Inventory Management System ===");
        System.out.println("1. Add Item");
        System.out.println("2. View Items");
        System.out.println("3. Create Order");
        System.out.println("4. View Orders");
        System.out.println("5. Update Item");
        System.out.println("6. Delete Item");
        System.out.println("7. Low Stock Report");
        System.out.println("8. Inventory Value Report");
        System.out.println("0. Exit");
    }

    private static void addItem(Connection conn, Scanner sc) {
        System.out.println("\n--- Add Item ---");
        String name = readNonEmptyLine(sc, "Name: ");
        int quantity = readInt(sc, "Quantity: ");
        BigDecimal price = readBigDecimal(sc, "Price: ");

        String sql = "INSERT INTO items (name, quantity, price) VALUES (?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, quantity);
            ps.setBigDecimal(3, price);
            int rows = ps.executeUpdate();

            if (rows > 0) {
                System.out.println("Item added.");
            } else {
                System.out.println("Item was not added.");
            }
        } catch (SQLException e) {
            System.out.println("Could not add item: " + e.getMessage());
        }
    }

    private static void viewItems(Connection conn) {
        System.out.println("\n--- Items ---");
        String sql = "SELECT id, name, quantity, price FROM items ORDER BY id";

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            System.out.printf("%-5s %-20s %-10s %-10s%n", "ID", "Name", "Quantity", "Price");
            System.out.println("-----------------------------------------------");

            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.printf("%-5d %-20s %-10d %-10.2f%n",
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getInt("quantity"),
                        rs.getBigDecimal("price"));
            }

            if (!found) {
                System.out.println("No items found.");
            }
        } catch (SQLException e) {
            System.out.println("Could not load items: " + e.getMessage());
        }
    }

    private static void createOrder(Connection conn, Scanner sc) {
        System.out.println("\n--- Create Order ---");
        int itemId = readInt(sc, "Item ID: ");
        int orderQty = readInt(sc, "Quantity: ");

        if (orderQty <= 0) {
            System.out.println("Quantity must be greater than 0.");
            return;
        }

        String checkSql = "SELECT quantity, name FROM items WHERE id = ?";
        String updateSql = "UPDATE items SET quantity = quantity - ? WHERE id = ?";
        String insertSql = "INSERT INTO orders (item_id, quantity) VALUES (?, ?)";

        try {
            conn.setAutoCommit(false);

            int currentStock;
            String itemName;

            try (PreparedStatement check = conn.prepareStatement(checkSql)) {
                check.setInt(1, itemId);

                try (ResultSet rs = check.executeQuery()) {
                    if (!rs.next()) {
                        System.out.println("Item not found.");
                        conn.rollback();
                        return;
                    }

                    currentStock = rs.getInt("quantity");
                    itemName = rs.getString("name");
                }
            }

            if (currentStock < orderQty) {
                System.out.println("Not enough stock for " + itemName + ". Available: " + currentStock);
                conn.rollback();
                return;
            }

            try (PreparedStatement update = conn.prepareStatement(updateSql)) {
                update.setInt(1, orderQty);
                update.setInt(2, itemId);
                update.executeUpdate();
            }

            try (PreparedStatement insert = conn.prepareStatement(insertSql)) {
                insert.setInt(1, itemId);
                insert.setInt(2, orderQty);
                insert.executeUpdate();
            }

            conn.commit();
            System.out.println("Order created successfully.");

        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {
            }
            System.out.println("Could not create order: " + e.getMessage());
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
    }

    private static void viewOrders(Connection conn) {
        System.out.println("\n--- Orders ---");
        String sql = """
                SELECT o.id, i.name, o.quantity, o.order_date
                FROM orders o
                JOIN items i ON o.item_id = i.id
                ORDER BY o.id
                """;

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            System.out.printf("%-5s %-20s %-10s %-20s%n", "ID", "Item", "Qty", "Date");
            System.out.println("------------------------------------------------------");

            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.printf("%-5d %-20s %-10d %-20s%n",
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getInt("quantity"),
                        String.valueOf(rs.getTimestamp("order_date")));
            }

            if (!found) {
                System.out.println("No orders found.");
            }
        } catch (SQLException e) {
            System.out.println("Could not load orders: " + e.getMessage());
        }
    }

    private static void updateItem(Connection conn, Scanner sc) {
        System.out.println("\n--- Update Item ---");
        int id = readInt(sc, "Item ID: ");
        String name = readNonEmptyLine(sc, "New name: ");
        int quantity = readInt(sc, "New quantity: ");
        BigDecimal price = readBigDecimal(sc, "New price: ");

        String sql = "UPDATE items SET name = ?, quantity = ?, price = ? WHERE id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, quantity);
            ps.setBigDecimal(3, price);
            ps.setInt(4, id);

            int rows = ps.executeUpdate();

            if (rows > 0) {
                System.out.println("Item updated.");
            } else {
                System.out.println("Item not found.");
            }
        } catch (SQLException e) {
            System.out.println("Could not update item: " + e.getMessage());
        }
    }

    private static void deleteItem(Connection conn, Scanner sc) {
        System.out.println("\n--- Delete Item ---");
        int id = readInt(sc, "Item ID: ");

        String sql = "DELETE FROM items WHERE id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);

            int rows = ps.executeUpdate();

            if (rows > 0) {
                System.out.println("Item deleted.");
            } else {
                System.out.println("Item not found.");
            }
        } catch (SQLException e) {
            System.out.println("Could not delete item. If there are orders linked to it, MySQL may block the delete.");
            System.out.println("Details: " + e.getMessage());
        }
    }

    private static void lowStockReport(Connection conn) {
        System.out.println("\n--- Low Stock Report ---");
        String sql = "SELECT id, name, quantity FROM items WHERE quantity < ? ORDER BY quantity ASC";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, LOW_STOCK_LIMIT);

            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.println(
                            rs.getInt("id") + " | " +
                                    rs.getString("name") + " | stock: " +
                                    rs.getInt("quantity")
                    );
                }

                if (!found) {
                    System.out.println("No low stock items.");
                }
            }
        } catch (SQLException e) {
            System.out.println("Could not generate report: " + e.getMessage());
        }
    }

    private static void inventoryValueReport(Connection conn) {
        System.out.println("\n--- Inventory Value Report ---");
        String sql = "SELECT SUM(quantity * price) AS total_value FROM items";

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            if (rs.next()) {
                BigDecimal total = rs.getBigDecimal("total_value");
                if (total == null) {
                    total = BigDecimal.ZERO;
                }
                System.out.println("Total inventory value: " + total);
            }
        } catch (SQLException e) {
            System.out.println("Could not calculate inventory value: " + e.getMessage());
        }
    }

    private static int readInt(Scanner sc, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = sc.nextLine().trim();
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Enter a valid whole number.");
            }
        }
    }

    private static BigDecimal readBigDecimal(Scanner sc, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = sc.nextLine().trim().replace(",", ".");
            try {
                return new BigDecimal(input);
            } catch (NumberFormatException e) {
                System.out.println("Enter a valid number, like 2.5 or 2,5.");
            }
        }
    }

    private static String readNonEmptyLine(Scanner sc, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = sc.nextLine().trim();
            if (!input.isEmpty()) {
                return input;
            }
            System.out.println("Value cannot be empty.");
        }
    }
}
