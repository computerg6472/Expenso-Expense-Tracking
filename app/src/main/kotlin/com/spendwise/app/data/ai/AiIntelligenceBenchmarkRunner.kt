package com.spendwise.app.data.ai

import com.spendwise.app.data.local.entity.Category
import com.spendwise.app.data.local.entity.Expense
import com.spendwise.app.domain.model.DefaultCategories

data class BenchmarkCase(
    val id: Int,
    val input: String,
    val expectedAmount: Double,
    val expectedCategory: String,
    val expectedNoteContains: String
)

object AiIntelligenceBenchmarkRunner {

    private val categories: List<Category> = DefaultCategories.ALL + listOf(
        Category(
            id = "cat_gym",
            name = "Gym",
            colorHex = "#10B981",
            isDefault = false,
            aliases = "gym, fitness, workout, trainer, cult, protein"
        ),
        Category(
            id = "cat_freelance",
            name = "Freelance",
            colorHex = "#3B82F6",
            isDefault = false,
            aliases = "freelance, client, upwork, fiverr, contract"
        ),
        Category(
            id = "cat_subscriptions",
            name = "Subscriptions",
            colorHex = "#8B5CF6",
            isDefault = false,
            aliases = "netflix, spotify, prime, hotstar, youtube premium, subscriptions, subscription"
        )
    )

    private val benchmarkDataset: List<BenchmarkCase> = listOf(
        // 1-15: Single line short commands
        BenchmarkCase(1, "chai 20", 20.0, "Food", "Chai"),
        BenchmarkCase(2, "metro 45", 45.0, "Travel", "Metro"),
        BenchmarkCase(3, "swiggy 420", 420.0, "Food", "Swiggy"),
        BenchmarkCase(4, "uber 250", 250.0, "Travel", "Uber"),
        BenchmarkCase(5, "paracetamol 50", 50.0, "Medical", "Paracetamol"),
        BenchmarkCase(6, "blinkit 350", 350.0, "Grocery", "Blinkit"),
        BenchmarkCase(7, "zepto 180", 180.0, "Grocery", "Zepto"),
        BenchmarkCase(8, "zomato 540", 540.0, "Food", "Zomato"),
        BenchmarkCase(9, "coffee 120", 120.0, "Food", "Coffee"),
        BenchmarkCase(10, "auto 60", 60.0, "Travel", "Auto"),
        BenchmarkCase(11, "dosa 80", 80.0, "Food", "Dosa"),
        BenchmarkCase(12, "petrol 500", 500.0, "Travel", "Petrol"),
        BenchmarkCase(13, "milk 65", 65.0, "Grocery", "Milk"),
        BenchmarkCase(14, "haircut 200", 200.0, "Hair Care", "Haircut"),
        BenchmarkCase(15, "shampoo 350", 350.0, "Hair Care", "Shampoo"),

        // 16-30: Natural speech with noise and polite fillers
        BenchmarkCase(16, "Add a medical expense my mum 1000 rupees", 1000.0, "Medical", "Mum"),
        BenchmarkCase(17, "spent 250 on coffee at starbucks today", 250.0, "Food", "Starbucks"),
        BenchmarkCase(18, "paid 15000 pg rent through gpay", 15000.0, "Rent", "PG rent"),
        BenchmarkCase(19, "bought paracetamol 50", 50.0, "Medical", "Paracetamol"),
        BenchmarkCase(20, "bhai kal raat ko uber kiya tha 340 ka", 340.0, "Travel", "Uber"),
        BenchmarkCase(21, "please log 150 for lunch today", 150.0, "Food", "Lunch"),
        BenchmarkCase(22, "hey expenso can you add 400 for groceries", 400.0, "Grocery", "Groceries"),
        BenchmarkCase(23, "i want to record 2200 for doctor consultation", 2200.0, "Medical", "Doctor"),
        BenchmarkCase(24, "expenso please record 80 for auto rickshaw", 80.0, "Travel", "Auto"),
        BenchmarkCase(25, "bro spent 550 on dinner at mcdonalds", 550.0, "Food", "Mcdonalds"),
        BenchmarkCase(26, "paid 12000 flat rent to landlord", 12000.0, "Rent", "Landlord"),
        BenchmarkCase(27, "log 180 spent on movie snacks", 180.0, "Food", "Snacks"),
        BenchmarkCase(28, "please enter 350 rupees for pharmacy medicine", 350.0, "Medical", "Pharmacy"),
        BenchmarkCase(29, "bought sunscreen lotion for 450", 450.0, "Skin Care", "Sunscreen"),
        BenchmarkCase(30, "spent 1200 at salon for haircut and beard", 1200.0, "Hair Care", "Haircut"),

        // 31-45: Unit multipliers ('k', 'lakh', 'lac', 'cr')
        BenchmarkCase(31, "1.5k for new shoes", 1500.0, "Orders (Online Shopping)", "Shoes"),
        BenchmarkCase(32, "15k for apartment rent", 15000.0, "Rent", "Apartment rent"),
        BenchmarkCase(33, "spent 2.5k on amazon shopping", 2500.0, "Orders (Online Shopping)", "Amazon"),
        BenchmarkCase(34, "paid 18k room rent", 18000.0, "Rent", "Room rent"),
        BenchmarkCase(35, "invested 10k in mutual funds sip", 10000.0, "Savings", "Mutual funds"),
        BenchmarkCase(36, "1.2k for blood test at clinic", 1200.0, "Medical", "Blood test"),
        BenchmarkCase(37, "bought flight tickets 6.5k", 6500.0, "Travel", "Flight"),
        BenchmarkCase(38, "paid 1 lakh for deposit", 100000.0, "Rent", "Deposit"),
        BenchmarkCase(39, "1.5 lakh hospital surgery bill", 150000.0, "Medical", "Hospital"),
        BenchmarkCase(40, "spent 20k on myntra clothes", 20000.0, "Orders (Online Shopping)", "Myntra"),
        BenchmarkCase(41, "3.5k flipkart order", 3500.0, "Orders (Online Shopping)", "Flipkart"),
        BenchmarkCase(42, "paid 5k for dental clinic braces", 5000.0, "Medical", "Dental"),
        BenchmarkCase(43, "spent 1.8k on dinner buffet", 1800.0, "Food", "Dinner"),
        BenchmarkCase(44, "log 4k for train reservation irctc", 4000.0, "Travel", "Train"),
        BenchmarkCase(45, "paid 25k for house rent", 25000.0, "Rent", "House rent"),

        // 46-60: Disambiguation between count, date, and amount
        BenchmarkCase(46, "2 pizzas for 600", 600.0, "Food", "Pizzas"),
        BenchmarkCase(47, "3 coffees for 360", 360.0, "Food", "Coffees"),
        BenchmarkCase(48, "4 burgers worth 520", 520.0, "Food", "Burgers"),
        BenchmarkCase(49, "15th rent 12000", 12000.0, "Rent", "Rent"),
        BenchmarkCase(50, "1st oct grocery 1500", 1500.0, "Grocery", "Grocery"),
        BenchmarkCase(51, "2 tshirts 900", 900.0, "Orders (Online Shopping)", "Tshirts"),
        BenchmarkCase(52, "5 medicines 450", 450.0, "Medical", "Medicines"),
        BenchmarkCase(53, "2 auto rides for 140", 140.0, "Travel", "Auto"),
        BenchmarkCase(54, "10 eggs for 70", 70.0, "Grocery", "Eggs"),
        BenchmarkCase(55, "2 cinema tickets 500", 500.0, "Others", "Cinema"),
        BenchmarkCase(56, "20th october flight 4500", 4500.0, "Travel", "Flight"),
        BenchmarkCase(57, "3 shawarmas for 330", 330.0, "Food", "Shawarmas"),
        BenchmarkCase(58, "6 bananas 40", 40.0, "Grocery", "Bananas"),
        BenchmarkCase(59, "2 shampoos 600", 600.0, "Hair Care", "Shampoos"),
        BenchmarkCase(60, "2 face serums 850", 850.0, "Skin Care", "Serums"),

        // 61-75: Custom categories ("Gym", "Freelance", "Subscriptions")
        BenchmarkCase(61, "paid 1500 for gym membership", 1500.0, "Gym", "Gym"),
        BenchmarkCase(62, "spent 3000 on whey protein", 3000.0, "Gym", "Protein"),
        BenchmarkCase(63, "cult fit monthly pass 2000", 2000.0, "Gym", "Cult"),
        BenchmarkCase(64, "personal trainer fee 4000", 4000.0, "Gym", "Trainer"),
        BenchmarkCase(65, "upwork service fee 500", 500.0, "Freelance", "Upwork"),
        BenchmarkCase(66, "freelance domain renewal 900", 900.0, "Freelance", "Domain"),
        BenchmarkCase(67, "paid client sub-contractor 5000", 5000.0, "Freelance", "Client"),
        BenchmarkCase(68, "netflix subscription 649", 649.0, "Subscriptions", "Netflix"),
        BenchmarkCase(69, "spotify premium 119", 119.0, "Subscriptions", "Spotify"),
        BenchmarkCase(70, "amazon prime yearly 1499", 1499.0, "Subscriptions", "Prime"),
        BenchmarkCase(71, "youtube premium 129", 129.0, "Subscriptions", "Youtube"),
        BenchmarkCase(72, "gym gloves 450", 450.0, "Gym", "Gloves"),
        BenchmarkCase(73, "hotstar super plan 899", 899.0, "Subscriptions", "Hotstar"),
        BenchmarkCase(74, "freelance invoicing software 1200", 1200.0, "Freelance", "Invoicing"),
        BenchmarkCase(75, "cult pass 1800", 1800.0, "Gym", "Cult"),

        // 76-90: Skin Care & Hair Care & Grocery nuances
        BenchmarkCase(76, "bought cetaphil moisturizer 450", 450.0, "Skin Care", "Cetaphil"),
        BenchmarkCase(77, "dermatologist consultation 800", 800.0, "Skin Care", "Dermatologist"),
        BenchmarkCase(78, "facial at kaya clinic 1500", 1500.0, "Skin Care", "Facial"),
        BenchmarkCase(79, "hair spa at loreal salon 1200", 1200.0, "Hair Care", "Hair spa"),
        BenchmarkCase(80, "hair color touchup 900", 900.0, "Hair Care", "Hair color"),
        BenchmarkCase(81, "beard trim 100", 100.0, "Hair Care", "Beard"),
        BenchmarkCase(82, "aloe vera gel 150", 150.0, "Skin Care", "Aloe"),
        BenchmarkCase(83, "instamart fresh fruits 280", 280.0, "Grocery", "Instamart"),
        BenchmarkCase(84, "supermarket vegetables 420", 420.0, "Grocery", "Vegetables"),
        BenchmarkCase(85, "dmart ration shopping 3200", 3200.0, "Grocery", "Dmart"),
        BenchmarkCase(86, "atta and cooking oil 650", 650.0, "Grocery", "Atta"),
        BenchmarkCase(87, "paneer and curd 140", 140.0, "Grocery", "Paneer"),
        BenchmarkCase(88, "nykaa beauty cosmetics 1100", 1100.0, "Orders (Online Shopping)", "Nykaa"),
        BenchmarkCase(89, "face wash and scrub 300", 300.0, "Skin Care", "Face wash"),
        BenchmarkCase(90, "shampoo and conditioner 550", 550.0, "Hair Care", "Shampoo"),

        // 91-105: Medical & Rent Edge Cases
        BenchmarkCase(91, "cough syrup 85", 85.0, "Medical", "Syrup"),
        BenchmarkCase(92, "hospital blood test 1500", 1500.0, "Medical", "Blood test"),
        BenchmarkCase(93, "dentist root canal 3500", 3500.0, "Medical", "Dentist"),
        BenchmarkCase(94, "apollo pharmacy medicines 620", 620.0, "Medical", "Apollo"),
        BenchmarkCase(95, "eye drops 120", 120.0, "Medical", "Drops"),
        BenchmarkCase(96, "maintenance fee for apartment 2500", 2500.0, "Rent", "Maintenance"),
        BenchmarkCase(97, "pg room deposit 5000", 5000.0, "Rent", "Deposit"),
        BenchmarkCase(98, "hostel rent 8000", 8000.0, "Rent", "Hostel"),
        BenchmarkCase(99, "flat brokerage fee 10000", 10000.0, "Rent", "Brokerage"),
        BenchmarkCase(100, "vitamin tablets 400", 400.0, "Medical", "Vitamin"),
        BenchmarkCase(101, "bandages and dettol 160", 160.0, "Medical", "Dettol"),
        BenchmarkCase(102, "clinic checkup 500", 500.0, "Medical", "Clinic"),
        BenchmarkCase(103, "room rent 9500", 9500.0, "Rent", "Room"),
        BenchmarkCase(104, "insulin prescription 750", 750.0, "Medical", "Insulin"),
        BenchmarkCase(105, "landlord electricity maintenance 1800", 1800.0, "Rent", "Electricity"),

        // 106-120: Unrelated topics falling strictly back to "Others"
        BenchmarkCase(106, "dog food pedigree 450", 450.0, "Others", "Pedigree"),
        BenchmarkCase(107, "cat vaccination 800", 800.0, "Others", "Vaccination"),
        BenchmarkCase(108, "charity temple donation 500", 500.0, "Others", "Donation"),
        BenchmarkCase(109, "car puncture repair 150", 150.0, "Others", "Puncture"),
        BenchmarkCase(110, "birthday gift for friend 1200", 1200.0, "Others", "Gift"),
        BenchmarkCase(111, "books on history 650", 650.0, "Others", "Books"),
        BenchmarkCase(112, "tuition fee for son 2000", 2000.0, "Others", "Tuition"),
        BenchmarkCase(113, "steam game purchase 999", 999.0, "Others", "Game"),
        BenchmarkCase(114, "guitar strings 400", 400.0, "Others", "Strings"),
        BenchmarkCase(115, "house painting labor 3000", 3000.0, "Others", "Painting"),
        BenchmarkCase(116, "aquarium fish food 180", 180.0, "Others", "Fish food"),
        BenchmarkCase(117, "speeding traffic fine 1000", 1000.0, "Others", "Traffic fine"),
        BenchmarkCase(118, "pottery workshop fee 750", 750.0, "Others", "Pottery"),
        BenchmarkCase(119, "dry cleaning suit 350", 350.0, "Others", "Dry cleaning"),
        BenchmarkCase(120, "watch battery replacement 200", 200.0, "Others", "Battery"),

        // 121-135: Travel, Commute, Fuel
        BenchmarkCase(121, "ola cab to airport 650", 650.0, "Travel", "Ola"),
        BenchmarkCase(122, "rapido bike ride 45", 45.0, "Travel", "Rapido"),
        BenchmarkCase(123, "highway toll tax 110", 110.0, "Travel", "Toll"),
        BenchmarkCase(124, "airport parking fee 200", 200.0, "Travel", "Parking"),
        BenchmarkCase(125, "diesel for car 2000", 2000.0, "Travel", "Diesel"),
        BenchmarkCase(126, "bus pass recharge 800", 800.0, "Travel", "Bus pass"),
        BenchmarkCase(127, "metro smart card 300", 300.0, "Travel", "Metro"),
        BenchmarkCase(128, "train sleeper ticket 450", 450.0, "Travel", "Train"),
        BenchmarkCase(129, "indigo flight web checkin 350", 350.0, "Travel", "Indigo"),
        BenchmarkCase(130, "auto fare 50", 50.0, "Travel", "Auto"),
        BenchmarkCase(131, "taxi ride home 220", 220.0, "Travel", "Taxi"),
        BenchmarkCase(132, "uber premier ride 480", 480.0, "Travel", "Uber"),
        BenchmarkCase(133, "petrol pump refill 1000", 1000.0, "Travel", "Petrol"),
        BenchmarkCase(134, "rickshaw from station 40", 40.0, "Travel", "Rickshaw"),
        BenchmarkCase(135, "irctc tatkal booking 1200", 1200.0, "Travel", "Irctc"),

        // 136-150: Savings, Online Shopping & Hindi Slang
        BenchmarkCase(136, "sip investment zerodha 5000", 5000.0, "Savings", "Zerodha"),
        BenchmarkCase(137, "bought gold coin 15000", 15000.0, "Savings", "Gold"),
        BenchmarkCase(138, "groww mutual fund deposit 2500", 2500.0, "Savings", "Groww"),
        BenchmarkCase(139, "fixed deposit transfer 50000", 50000.0, "Savings", "Deposit"),
        BenchmarkCase(140, "amazon electronics order 2400", 2400.0, "Orders (Online Shopping)", "Amazon"),
        BenchmarkCase(141, "flipkart earphones 899", 899.0, "Orders (Online Shopping)", "Flipkart"),
        BenchmarkCase(142, "meesho kitchen set 450", 450.0, "Orders (Online Shopping)", "Meesho"),
        BenchmarkCase(143, "myntra jacket 1800", 1800.0, "Orders (Online Shopping)", "Myntra"),
        BenchmarkCase(144, "ajio sneakers 1400", 1400.0, "Orders (Online Shopping)", "Ajio"),
        BenchmarkCase(145, "aaj dmart me 2400 ka saman liya", 2400.0, "Grocery", "Dmart"),
        BenchmarkCase(146, "bhai kal swiggy se 350 ka khana mangwaya", 350.0, "Food", "Swiggy"),
        BenchmarkCase(147, "auto wale ko 90 rupay diye", 90.0, "Travel", "Auto"),
        BenchmarkCase(148, "doctor ko 600 fees diya", 600.0, "Medical", "Doctor"),
        BenchmarkCase(149, "room ka rent 8500 transfer kiya", 8500.0, "Rent", "Rent"),
        BenchmarkCase(150, "barber ko 150 haircut ke liye diye", 150.0, "Hair Care", "Haircut")
    )

    fun runBenchmark(): Boolean {
        var categoryMatches = 0
        var amountMatches = 0
        var noteMatches = 0

        val categoryMismatches = mutableListOf<String>()
        val amountMismatches = mutableListOf<String>()
        val noteMismatches = mutableListOf<String>()

        for (case in benchmarkDataset) {
            // 1. Amount Parsing
            val parsedAmount = AiExpenseParser.parseAmount(case.input)
            val amtVal = parsedAmount?.amount ?: 0.0
            if (Math.abs(amtVal - case.expectedAmount) < 0.01) {
                amountMatches++
            } else {
                amountMismatches.add("[Case ${case.id}] '${case.input}' -> Expected ${case.expectedAmount}, got $amtVal")
            }

            // 2. Category Classification
            val matchRes = AiExpenseParser.matchCategory(case.input, categories)
            val matchedCatName = matchRes.category.name
            if (matchedCatName.equals(case.expectedCategory, ignoreCase = true)) {
                categoryMatches++
            } else {
                categoryMismatches.add("[Case ${case.id}] '${case.input}' -> Expected '${case.expectedCategory}', got '${matchedCatName}' (Score: ${matchRes.score}, Reason: ${matchRes.matchReason})")
            }

            // 3. Note Cleaning
            val cleanedNote = AiExpenseParser.cleanExpenseNote(case.input, matchRes.category.name)
            if (cleanedNote.contains(case.expectedNoteContains, ignoreCase = true)) {
                noteMatches++
            } else {
                noteMismatches.add("[Case ${case.id}] '${case.input}' -> Expected note containing '${case.expectedNoteContains}', got '${cleanedNote}'")
            }
        }

        val total = benchmarkDataset.size.toDouble()
        val amountAccuracy = (amountMatches / total) * 100.0
        val categoryAccuracy = (categoryMatches / total) * 100.0
        val noteAccuracy = (noteMatches / total) * 100.0

        println("==================================================")
        println("      AI INTELLIGENCE 150 BENCHMARK RESULTS       ")
        println("==================================================")
        println("Amount Accuracy:   ${String.format("%.2f", amountAccuracy)}% ($amountMatches / ${benchmarkDataset.size})")
        println("Category Accuracy: ${String.format("%.2f", categoryAccuracy)}% ($categoryMatches / ${benchmarkDataset.size})")
        println("Note Accuracy:     ${String.format("%.2f", noteAccuracy)}% ($noteMatches / ${benchmarkDataset.size})")
        println("==================================================")

        if (amountMismatches.isNotEmpty()) {
            println("Amount Mismatches (${amountMismatches.size}):")
            amountMismatches.forEach { println("  $it") }
        }
        if (categoryMismatches.isNotEmpty()) {
            println("Category Mismatches (${categoryMismatches.size}):")
            categoryMismatches.forEach { println("  $it") }
        }
        if (noteMismatches.isNotEmpty()) {
            println("Note Mismatches (${noteMismatches.size}):")
            noteMismatches.forEach { println("  $it") }
        }

        // Test Reference Resolution
        val pastExpenses = listOf(
            Expense(id = "exp-1", amount = 250.0, paymentSource = "INCOME", categoryId = DefaultCategories.ID_FOOD, note = "Coffee at Starbucks", dateEpoch = 1000L, year = 2026, month = 10),
            Expense(id = "exp-2", amount = 15000.0, paymentSource = "INCOME", categoryId = DefaultCategories.ID_RENT, note = "PG rent", dateEpoch = 2000L, year = 2026, month = 10),
            Expense(id = "exp-3", amount = 500.0, paymentSource = "INCOME", categoryId = DefaultCategories.ID_MEDICAL, note = "Doctor consultation", dateEpoch = 3000L, year = 2026, month = 10)
        )
        val res1 = AiExpenseParser.resolveExpenseReference("delete the last one", pastExpenses, categories)
        val res2 = AiExpenseParser.resolveExpenseReference("update the starbucks one to 300", pastExpenses, categories)
        val res3 = AiExpenseParser.resolveExpenseReference("remove the medical expense", pastExpenses, categories)

        val refResolutionPassed = res1?.id == "exp-3" && res2?.id == "exp-1" && res3?.id == "exp-3"
        println("Reference Resolution Passed: $refResolutionPassed")

        val passed = amountAccuracy >= 98.0 && categoryAccuracy >= 95.0 && noteAccuracy >= 95.0 && refResolutionPassed
        println("Benchmark Outcome: ${if (passed) "ALL CRITERIA PASSED" else "FAILED"}")
        return passed
    }
}

fun main() {
    val passed = AiIntelligenceBenchmarkRunner.runBenchmark()
    if (!passed) {
        System.exit(1)
    }
}
