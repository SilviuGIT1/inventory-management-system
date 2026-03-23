module ax.it.inventory {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;


    opens ax.it.inventory to javafx.fxml;
    exports ax.it.inventory;
}