# Inventory Management System

A console-based Inventory Management System built in Java with a MySQL database backend.

## Features

- Add new items
- View all items
- Update existing items
- Delete items
- Create orders and reduce stock automatically
- View order history
- Generate low stock reports
- Calculate total inventory value

## Technologies Used

- Java
- JDBC
- MySQL
- Maven

## Database Structure

### items
- id
- name
- quantity
- price

### orders
- id
- item_id
- quantity
- order_date

## Setup Instructions

### 1. Clone the repository

```bash
git clone https://github.com/SilviuGIT1/inventory-management-system.git
