package com.spendwise.app.domain.model

import com.spendwise.app.data.local.entity.Category
import java.util.UUID

object DefaultCategories {
    // Deterministic UUIDs based on category name so they are identical across all devices
    val ID_FOOD: String = UUID.nameUUIDFromBytes("spendwise_category_food".toByteArray()).toString()
    val ID_RENT: String = UUID.nameUUIDFromBytes("spendwise_category_rent".toByteArray()).toString()
    val ID_MEDICAL: String = UUID.nameUUIDFromBytes("spendwise_category_medical".toByteArray()).toString()
    val ID_GROCERY: String = UUID.nameUUIDFromBytes("spendwise_category_grocery".toByteArray()).toString()
    val ID_SAVINGS: String = UUID.nameUUIDFromBytes("spendwise_category_savings".toByteArray()).toString()
    val ID_TRAVEL: String = UUID.nameUUIDFromBytes("spendwise_category_travel".toByteArray()).toString()
    val ID_HAIR_CARE: String = UUID.nameUUIDFromBytes("spendwise_category_hair_care".toByteArray()).toString()
    val ID_SKIN_CARE: String = UUID.nameUUIDFromBytes("spendwise_category_skin_care".toByteArray()).toString()
    val ID_ORDERS: String = UUID.nameUUIDFromBytes("spendwise_category_orders_online_shopping".toByteArray()).toString()

    // Deterministic ID for Others - fixed string as requested: "cat_others"
    const val ID_OTHERS: String = "cat_others"

    val CATEGORY_OTHERS = Category(
        id = ID_OTHERS,
        name = "Others",
        colorHex = "#6B7280",
        iconName = "category",
        isDefault = true,
        sortOrder = 9999,
        aliases = "other, miscellaneous, misc, general, unknown, etc, expenses"
    )

    val ALL: List<Category> = listOf(
        Category(id = ID_FOOD, name = "Food", colorHex = "#FF6B6B", iconName = "restaurant", isDefault = true, sortOrder = 0, aliases = "food, dining, restaurant, swiggy, zomato, cafe, lunch, dinner, snacks"),
        Category(id = ID_RENT, name = "Rent", colorHex = "#4ECDC4", iconName = "home", isDefault = true, sortOrder = 1, aliases = "rent, flat, landlord, house, apartment, maintenance"),
        Category(id = ID_MEDICAL, name = "Medical", colorHex = "#45B7D1", iconName = "medical_services", isDefault = true, sortOrder = 2, aliases = "medicine, doctor, hospital, pharmacy, clinic, healthcare, pharma"),
        Category(id = ID_GROCERY, name = "Grocery", colorHex = "#96CEB4", iconName = "shopping_cart", isDefault = true, sortOrder = 3, aliases = "grocery, supermarket, blinkit, zepto, instamart, milk, vegetables, fruits"),
        Category(id = ID_SAVINGS, name = "Savings", colorHex = "#FFEAA7", iconName = "savings", isDefault = true, sortOrder = 4, aliases = "savings, investment, mutual fund, stocks, deposit, sip, gold"),
        Category(id = ID_TRAVEL, name = "Travel", colorHex = "#DDA0DD", iconName = "flight", isDefault = true, sortOrder = 5, aliases = "travel, flight, train, uber, ola, taxi, fuel, petrol, bus, metro, cab"),
        Category(id = ID_HAIR_CARE, name = "Hair Care", colorHex = "#EC4899", iconName = "content_cut", isDefault = true, sortOrder = 6, aliases = "hair, salon, haircut, shampoo, barber, styling"),
        Category(id = ID_SKIN_CARE, name = "Skin Care", colorHex = "#8B5CF6", iconName = "spa", isDefault = true, sortOrder = 7, aliases = "skin, skincare, cosmetics, dermatologist, facial, lotion, beauty"),
        Category(id = ID_ORDERS, name = "Orders (Online Shopping)", colorHex = "#F97316", iconName = "shopping_bag", isDefault = true, sortOrder = 8, aliases = "orders, online shopping, amazon, flipkart, myntra, shopping, ecommerce"),
        CATEGORY_OTHERS
    )
}
