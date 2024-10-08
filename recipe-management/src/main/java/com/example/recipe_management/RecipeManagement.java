package com.example.recipe_management;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class RecipeManagement{
    private static final String URL = "jdbc:mysql://localhost:3306/receiptmanagement";
    private static final String USER = "root";
    private static final String PASSWORD = "root";

    private static Connection connection;
    private static PreparedStatement preparedStatement;
    private static ResultSet resultSet;

    // Cache to store frequently accessed recipes by ID
    public static Map<Integer, Recipe> recipeCache = new HashMap<>();
    public int cache_size=2;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        try {
            connection = DriverManager.getConnection(URL, USER, PASSWORD);

            int choice;
            do {
                System.out.println("Menu:");
                System.out.println("1. Create Recipe");
                System.out.println("2. Read Recipe");
                System.out.println("3. Update Recipe");
                System.out.println("4. Delete Recipe");
                System.out.println("5. Exit");
                System.out.print("Enter your choice: ");
                choice = scanner.nextInt();

                switch (choice) {
                    case 1:
                        createRecord(scanner);
                        break;
                    case 2:
                        readRecord(scanner);
                        break;
                    case 3:
                        updateRecord(scanner);
                        break;
                    case 4:
                        deleteRecord(scanner);
                        break;
                    case 5:
                        System.out.println("Exiting...");
                        break;
                    default:
                        System.out.println("Invalid choice. Please try again.");
                }
            } while (choice != 5);

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (resultSet != null) resultSet.close();
                if (preparedStatement != null) preparedStatement.close();
                if (connection != null) connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    // Method to create a new recipe record
    private static void createRecord(Scanner scanner) {
        String sql = "INSERT INTO recipes (name, ingredients, instructions, prep_time) VALUES (?, ?, ?, ?)";
        try {
            System.out.print("Enter recipe name: ");
            scanner.nextLine();
            String name = scanner.nextLine();
            System.out.print("Enter ingredients: ");
            String ingredients = scanner.nextLine();
            System.out.print("Enter instructions: ");
            String instructions = scanner.nextLine();
            System.out.print("Enter preparation time (minutes): ");
            int prepTime = scanner.nextInt();

            preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setString(1, name);
            preparedStatement.setString(2, ingredients);
            preparedStatement.setString(3, instructions);
            preparedStatement.setInt(4, prepTime);
            preparedStatement.executeUpdate();
            System.out.println("Recipe created successfully!");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Method to read a recipe record, with caching
    private static void readRecord(Scanner scanner) {
        System.out.print("Enter recipe ID to fetch: ");
        int id = scanner.nextInt();

        ExecutorService executorService = Executors.newFixedThreadPool(2);

        // Task to fetch the recipe from the cache
        Callable<Recipe> cacheTask = () -> {
            if (recipeCache.containsKey(id)) {
                System.out.println("Fetching recipe from cache...");
                return recipeCache.get(id);
            }
            return null;
        };

        // Task to fetch the recipe from the database
        Callable<Recipe> dbTask = () -> {
            String sql = "SELECT * FROM recipes WHERE id = ?";
            try {
                preparedStatement = connection.prepareStatement(sql);
                preparedStatement.setInt(1, id);
                resultSet = preparedStatement.executeQuery();

                if (resultSet.next()) {
                    String name = resultSet.getString("name");
                    String ingredients = resultSet.getString("ingredients");
                    String instructions = resultSet.getString("instructions");
                    int prepTime = resultSet.getInt("prep_time");

                    Recipe recipe = new Recipe(id, name, ingredients, instructions, prepTime);
                    // Cache the fetched recipe
                    recipeCache.put(id, recipe);
                    System.out.println("Fetching recipe from database...");
                    return recipe;
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        };

        try {
            // Submit both tasks to the ExecutorService
            Future<Recipe> cacheFuture = executorService.submit(cacheTask);
            Future<Recipe> dbFuture = executorService.submit(dbTask);

            // Try to get the result from the cache
            Recipe recipe = cacheFuture.get();
            if (recipe == null) {
                // If cache doesn't have it, fetch from the database
                recipe = dbFuture.get();
            }

            if (recipe != null) {
                System.out.println(recipe);
            } else {
                System.out.println("Recipe not found.");
            }

        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        } finally {
            // Shutdown the executor service
            executorService.shutdown();
        }
    }

    // Method to update a recipe record
    private static void updateRecord(Scanner scanner) {
        System.out.print("Enter recipe ID to update: ");
        int id = scanner.nextInt();
        scanner.nextLine(); // Clear the buffer

        System.out.print("Enter new preparation time (minutes): ");
        int newPrepTime = scanner.nextInt();

        String sql = "UPDATE recipes SET prep_time = ? WHERE id = ?";
        try {
            preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setInt(1, newPrepTime);
            preparedStatement.setInt(2, id);
            int rowsAffected = preparedStatement.executeUpdate();

            if (rowsAffected > 0) {
                System.out.println("Recipe updated successfully!");
                // Update the cache if it exists
                if (recipeCache.containsKey(id)) {
                    Recipe cachedRecipe = recipeCache.get(id);
                    cachedRecipe.setPrepTime(newPrepTime);
                }
            } else {
                System.out.println("Recipe not found.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Method to delete a recipe record
    private static void deleteRecord(Scanner scanner) {
        System.out.print("Enter recipe ID to delete: ");
        int id = scanner.nextInt();
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        // Callable to delete the recipe from the cache
        Callable<String> cacheTask = () -> {
            if (recipeCache.containsKey(id)) {
                recipeCache.remove(id);
                return "Recipe deleted from cache.";
            } else {
                return "Recipe not found in cache.";
            }
        };

        Callable<String> dbTask = () -> {
            String sql = "DELETE FROM recipes WHERE id = ?";
            try {
                preparedStatement = connection.prepareStatement(sql);
                preparedStatement.setInt(1, id);
                int rowsAffected = preparedStatement.executeUpdate();

                if (rowsAffected > 0) {
                    return "Recipe deleted from database.";
                } else {
                    return "Recipe not found in database.";
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return "Error occurred while deleting from database.";
            }
        };

        Future<String> cacheResult = executorService.submit(cacheTask);
        Future<String> dbResult = executorService.submit(dbTask);

        executorService.shutdown();
        try {
            System.out.println(cacheResult.get());
            System.out.println(dbResult.get());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }
}

// Recipe class to represent a recipe object
class Recipe extends RecipeManagement {
    private int id;
    private String name;
    private String ingredients;
    private String instructions;
    private int prepTime;
    //private static Map<Integer, Recipe> recipeCache = new LinkedHashMap<>();
    //public int cache_size=5;

    public Recipe(int id, String name, String ingredients, String instructions, int prepTime) {
        this.id = id;
        this.name = name;
        this.ingredients = ingredients;
        this.instructions = instructions;
        this.prepTime = prepTime;
    }

    public void setPrepTime(int prepTime) {
        this.prepTime = prepTime;
    }

    @Override
    public String toString() {
        return "ID: " + id + ", Name: " + name + ", Ingredients: " + ingredients +
                ", Instructions: " + instructions + ", Preparation Time: " + prepTime + " minutes";
    }
    public void addTocache(int id,Recipe receipe){
        if(recipeCache.size()>=cache_size){
            int key=recipeCache.keySet().iterator().next();
            recipeCache.remove(key);
        }
        recipeCache.put(id,receipe);

    }
}