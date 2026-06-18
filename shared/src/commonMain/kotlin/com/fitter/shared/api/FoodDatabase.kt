package com.fitter.shared.api

data class FoodDbEntry(
    val name: String,
    val synonyms: List<String>,
    val calories: Double, // per 100g
    val protein: Float,   // per 100g
    val carbs: Float,     // per 100g
    val fat: Float        // per 100g
)

object FoodDatabase {
    val foods: List<FoodDbEntry> = listOf(
        FoodDbEntry("Chicken Breast", listOf("chicken breast", "chicken", "chicken breast, boneless, skinless", "cooked chicken breast", "grilled chicken breast"), 165.0, 31.0f, 0.0f, 3.6f),
        FoodDbEntry("Chicken Thigh", listOf("chicken thigh", "chicken thighs", "chicken leg"), 209.0, 26.0f, 0.0f, 10.9f),
        FoodDbEntry("Ground Beef", listOf("ground beef", "beef", "minced beef", "beef mince", "hamburger meat"), 254.0, 17.2f, 0.0f, 20.0f),
        FoodDbEntry("Beef Ribeye Steak", listOf("beef ribeye", "ribeye steak", "ribeye", "beef steak", "steak"), 291.0, 24.0f, 0.0f, 21.8f),
        FoodDbEntry("Salmon", listOf("salmon", "salmon fillet", "salmon steak", "wild salmon", "grilled salmon", "cooked salmon"), 208.0, 20.0f, 0.0f, 13.0f),
        FoodDbEntry("Tuna", listOf("tuna", "canned tuna", "tuna in water", "tuna fish"), 116.0, 26.0f, 0.0f, 1.0f),
        FoodDbEntry("Shrimp", listOf("shrimp", "shrimps", "prawn", "prawns"), 99.0, 24.0f, 0.2f, 0.3f),
        FoodDbEntry("Whole Egg", listOf("egg", "eggs", "whole egg", "boiled egg", "scrambled egg", "fried egg"), 155.0, 13.0f, 1.1f, 11.0f),
        FoodDbEntry("Egg White", listOf("egg white", "egg whites", "liquid egg white"), 52.0, 11.0f, 0.7f, 0.2f),
        FoodDbEntry("White Rice", listOf("white rice", "cooked white rice", "steamed rice", "jasmine rice", "basmati rice"), 130.0, 2.7f, 28.0f, 0.3f),
        FoodDbEntry("Brown Rice", listOf("brown rice", "cooked brown rice", "whole grain rice"), 111.0, 2.6f, 23.0f, 0.9f),
        FoodDbEntry("Oats", listOf("oats", "oatmeal", "rolled oats", "porridge oats"), 389.0, 16.9f, 66.3f, 6.9f),
        FoodDbEntry("Quinoa", listOf("quinoa", "cooked quinoa"), 120.0, 4.4f, 21.3f, 1.9f),
        FoodDbEntry("Whole Wheat Bread", listOf("whole wheat bread", "brown bread", "wholemeal bread", "wheat bread"), 247.0, 13.0f, 41.0f, 3.4f),
        FoodDbEntry("White Bread", listOf("white bread", "sliced white bread", "toast bread"), 265.0, 9.0f, 49.0f, 3.2f),
        FoodDbEntry("Pasta", listOf("pasta", "spaghetti", "macaroni", "cooked pasta", "noodles"), 131.0, 5.0f, 25.0f, 1.1f),
        FoodDbEntry("Potato", listOf("potato", "potatoes", "boiled potato", "baked potato", "russet potato"), 77.0, 2.0f, 17.0f, 0.1f),
        FoodDbEntry("Sweet Potato", listOf("sweet potato", "sweet potatoes", "baked sweet potato"), 86.0, 1.6f, 20.1f, 0.1f),
        FoodDbEntry("Broccoli", listOf("broccoli", "steamed broccoli", "raw broccoli"), 34.0, 2.8f, 6.6f, 0.4f),
        FoodDbEntry("Spinach", listOf("spinach", "baby spinach", "spinach leaves", "cooked spinach"), 23.0, 2.9f, 3.6f, 0.4f),
        FoodDbEntry("Avocado", listOf("avocado", "avocados", "guacamole"), 160.0, 2.0f, 8.5f, 14.7f),
        FoodDbEntry("Banana", listOf("banana", "bananas"), 89.0, 1.1f, 22.8f, 0.3f),
        FoodDbEntry("Apple", listOf("apple", "apples", "red apple", "green apple"), 52.0, 0.3f, 13.8f, 0.2f),
        FoodDbEntry("Orange", listOf("orange", "oranges", "clementine", "mandarin"), 47.0, 0.9f, 11.8f, 0.1f),
        FoodDbEntry("Blueberries", listOf("blueberries", "blueberry"), 57.0, 0.7f, 14.5f, 0.3f),
        FoodDbEntry("Strawberries", listOf("strawberries", "strawberry"), 32.0, 0.7f, 7.7f, 0.3f),
        FoodDbEntry("Milk (Whole)", listOf("milk", "whole milk", "full fat milk", "cow milk"), 61.0, 3.2f, 4.8f, 3.3f),
        FoodDbEntry("Milk (Skim)", listOf("skim milk", "fat free milk", "nonfat milk", "skimmed milk"), 34.0, 3.4f, 5.0f, 0.1f),
        FoodDbEntry("Greek Yogurt", listOf("greek yogurt", "plain greek yogurt", "nonfat greek yogurt", "fage yogurt"), 59.0, 10.0f, 3.6f, 0.4f),
        FoodDbEntry("Cheddar Cheese", listOf("cheddar cheese", "cheddar", "cheese slice"), 403.0, 25.0f, 1.3f, 33.0f),
        FoodDbEntry("Butter", listOf("butter", "salted butter", "unsalted butter"), 717.0, 0.9f, 0.1f, 81.0f),
        FoodDbEntry("Olive Oil", listOf("olive oil", "extra virgin olive oil", "evo"), 884.0, 0.0f, 0.0f, 100.0f),
        FoodDbEntry("Peanut Butter", listOf("peanut butter", "smooth peanut butter", "crunchy peanut butter"), 588.0, 25.0f, 20.0f, 50.0f),
        FoodDbEntry("Almonds", listOf("almonds", "almond"), 579.0, 21.2f, 21.7f, 49.9f),
        FoodDbEntry("Black Beans", listOf("black beans", "cooked black beans", "canned black beans"), 132.0, 8.9f, 23.7f, 0.5f),
        FoodDbEntry("Chickpeas", listOf("chickpeas", "garbanzo beans", "cooked chickpeas", "chana"), 164.0, 8.9f, 27.4f, 2.6f),
        FoodDbEntry("Tofu", listOf("tofu", "firm tofu", "bean curd"), 76.0, 8.0f, 1.9f, 4.8f),
        FoodDbEntry("Pork Chop", listOf("pork chop", "pork loin chop", "pork"), 242.0, 27.0f, 0.0f, 14.0f),
        FoodDbEntry("Turkey Breast", listOf("turkey breast", "sliced turkey", "deli turkey"), 135.0, 30.0f, 0.0f, 1.0f),
        FoodDbEntry("Cod", listOf("cod", "cod fish", "atlantic cod"), 82.0, 18.0f, 0.0f, 0.7f),
        FoodDbEntry("Tilapia", listOf("tilapia", "tilapia fillet"), 129.0, 26.0f, 0.0f, 2.7f),
        FoodDbEntry("Bacon", listOf("bacon", "cooked bacon", "bacon strip", "pork bacon"), 541.0, 37.0f, 1.4f, 42.0f),
        FoodDbEntry("Sausage", listOf("sausage", "sausages", "pork sausage", "beef sausage"), 301.0, 12.0f, 2.0f, 27.0f),
        FoodDbEntry("Cottage Cheese", listOf("cottage cheese", "paneer"), 98.0, 11.0f, 3.4f, 4.3f),
        FoodDbEntry("Mozzarella Cheese", listOf("mozzarella", "mozzarella cheese", "fresh mozzarella"), 280.0, 22.0f, 2.2f, 20.0f),
        FoodDbEntry("Parmesan Cheese", listOf("parmesan", "parmesan cheese", "grated parmesan"), 431.0, 38.0f, 4.1f, 29.0f),
        FoodDbEntry("Cream Cheese", listOf("cream cheese", "philadelphia cream cheese"), 342.0, 6.0f, 4.1f, 34.0f),
        FoodDbEntry("Kidney Beans", listOf("kidney beans", "cooked kidney beans", "canned kidney beans"), 127.0, 8.7f, 22.8f, 0.5f),
        FoodDbEntry("Lentils", listOf("lentils", "cooked lentils", "brown lentils", "red lentils"), 116.0, 9.0f, 20.0f, 0.4f),
        FoodDbEntry("Edamame", listOf("edamame", "soybeans", "steamed edamame"), 122.0, 11.0f, 10.0f, 5.0f),
        FoodDbEntry("Tempeh", listOf("tempeh", "fermented soy"), 192.0, 20.3f, 7.6f, 10.8f),
        FoodDbEntry("Walnuts", listOf("walnuts", "walnut"), 654.0, 15.2f, 13.7f, 65.2f),
        FoodDbEntry("Cashews", listOf("cashews", "cashew nuts", "cashew"), 553.0, 18.2f, 30.2f, 43.8f),
        FoodDbEntry("Peanuts", listOf("peanuts", "peanut"), 567.0, 25.8f, 16.1f, 49.2f),
        FoodDbEntry("Chia Seeds", listOf("chia seeds", "chia seed", "chia"), 486.0, 16.5f, 42.1f, 30.7f),
        FoodDbEntry("Flaxseeds", listOf("flaxseeds", "flax seed", "linseed", "ground flaxseed"), 534.0, 18.3f, 28.9f, 42.2f),
        FoodDbEntry("Pumpkin Seeds", listOf("pumpkin seeds", "pepitas"), 559.0, 30.2f, 10.7f, 49.0f),
        FoodDbEntry("Sunflower Seeds", listOf("sunflower seeds", "sunflower seed"), 584.0, 20.8f, 20.0f, 51.5f),
        FoodDbEntry("Coconut Oil", listOf("coconut oil", "virgin coconut oil"), 862.0, 0.0f, 0.0f, 100.0f),
        FoodDbEntry("Canola Oil", listOf("canola oil", "rapeseed oil"), 884.0, 0.0f, 0.0f, 100.0f),
        FoodDbEntry("Honey", listOf("honey", "raw honey"), 304.0, 0.3f, 82.4f, 0.0f),
        FoodDbEntry("Maple Syrup", listOf("maple syrup", "pancake syrup"), 260.0, 0.0f, 67.0f, 0.1f),
        FoodDbEntry("Dark Chocolate", listOf("dark chocolate", "cocoa chocolate"), 598.0, 7.8f, 45.9f, 42.6f),
        FoodDbEntry("Milk Chocolate", listOf("milk chocolate", "chocolate bar"), 535.0, 7.6f, 59.4f, 29.7f),
        FoodDbEntry("Tomato", listOf("tomato", "tomatoes", "cherry tomato", "cherry tomatoes"), 18.0, 0.9f, 3.9f, 0.2f),
        FoodDbEntry("Cucumber", listOf("cucumber", "cucumbers", "sliced cucumber"), 15.0, 0.6f, 3.6f, 0.1f),
        FoodDbEntry("Bell Pepper", listOf("bell pepper", "sweet pepper", "red bell pepper", "green bell pepper", "capsicum"), 20.0, 0.9f, 4.6f, 0.2f),
        FoodDbEntry("Zucchini", listOf("zucchini", "courgette"), 17.0, 1.2f, 3.1f, 0.3f),
        FoodDbEntry("Cauliflower", listOf("cauliflower", "cauliflower rice"), 25.0, 1.9f, 5.0f, 0.3f),
        FoodDbEntry("Cabbage", listOf("cabbage", "green cabbage", "red cabbage"), 25.0, 1.3f, 5.8f, 0.1f),
        FoodDbEntry("Asparagus", listOf("asparagus", "grilled asparagus"), 20.0, 2.2f, 3.9f, 0.1f),
        FoodDbEntry("Brussels Sprouts", listOf("brussels sprouts", "brussel sprouts"), 43.0, 3.4f, 9.0f, 0.3f),
        FoodDbEntry("Green Beans", listOf("green beans", "string beans"), 31.0, 1.8f, 7.0f, 0.2f),
        FoodDbEntry("Peas", listOf("peas", "green peas", "garden peas"), 81.0, 5.4f, 14.5f, 0.4f),
        FoodDbEntry("Sweet Corn", listOf("corn", "sweet corn", "canned corn", "maize"), 86.0, 3.2f, 19.0f, 1.2f),
        FoodDbEntry("Celery", listOf("celery", "celery sticks"), 16.0, 0.7f, 3.0f, 0.2f),
        FoodDbEntry("Eggplant", listOf("eggplant", "aubergine"), 25.0, 1.0f, 6.0f, 0.2f),
        FoodDbEntry("Mushroom", listOf("mushroom", "mushrooms", "button mushroom", "portobello mushroom"), 22.0, 3.1f, 3.3f, 0.3f),
        FoodDbEntry("Carrot", listOf("carrot", "carrots", "baby carrots"), 41.0, 0.9f, 9.6f, 0.2f),
        FoodDbEntry("Kale", listOf("kale", "kale leaves"), 49.0, 4.3f, 8.8f, 0.9f),
        FoodDbEntry("Lettuce", listOf("lettuce", "romaine lettuce", "iceberg lettuce", "salad greens"), 15.0, 1.4f, 2.9f, 0.2f),
        FoodDbEntry("Onion", listOf("onion", "onions", "red onion", "yellow onion"), 40.0, 1.1f, 9.3f, 0.1f),
        FoodDbEntry("Garlic", listOf("garlic", "garlic clove"), 149.0, 6.4f, 33.1f, 0.5f),
        FoodDbEntry("Orange Juice", listOf("orange juice", "oj"), 45.0, 0.7f, 10.4f, 0.2f),
        FoodDbEntry("Apple Juice", listOf("apple juice", "cider"), 46.0, 0.1f, 11.3f, 0.1f),
        FoodDbEntry("Mango", listOf("mango", "mangos"), 60.0, 0.8f, 15.0f, 0.4f),
        FoodDbEntry("Pineapple", listOf("pineapple", "pineapples"), 50.0, 0.5f, 13.1f, 0.1f),
        FoodDbEntry("Watermelon", listOf("watermelon", "watermelons"), 30.0, 0.6f, 7.6f, 0.2f),
        FoodDbEntry("Peach", listOf("peach", "peaches"), 39.0, 0.9f, 9.5f, 0.3f),
        FoodDbEntry("Pear", listOf("pear", "pears"), 57.0, 0.4f, 15.2f, 0.1f),
        FoodDbEntry("Plum", listOf("plum", "plums"), 46.0, 0.7f, 11.4f, 0.3f),
        FoodDbEntry("Cherry", listOf("cherry", "cherries", "sweet cherry"), 50.0, 1.0f, 12.0f, 0.3f),
        FoodDbEntry("Lemon", listOf("lemon", "lemons"), 29.0, 1.1f, 9.3f, 0.3f),
        FoodDbEntry("Lime", listOf("lime", "limes"), 30.0, 0.7f, 10.5f, 0.2f),
        FoodDbEntry("Grapefruit", listOf("grapefruit", "grapefruits"), 42.0, 0.8f, 10.7f, 0.1f),
        FoodDbEntry("Grapes", listOf("grape", "grapes", "green grapes", "red grapes"), 69.0, 0.7f, 18.1f, 0.2f),
        FoodDbEntry("Blue Cheese", listOf("blue cheese", "gorgonzola"), 353.0, 21.4f, 2.3f, 28.7f),
        FoodDbEntry("Feta Cheese", listOf("feta cheese", "feta"), 264.0, 14.0f, 4.1f, 21.0f),
        FoodDbEntry("Goat Cheese", listOf("goat cheese", "chevre"), 364.0, 22.0f, 0.0f, 30.0f),
        FoodDbEntry("Sour Cream", listOf("sour cream"), 198.0, 2.4f, 4.6f, 19.4f),
        FoodDbEntry("Heavy Cream", listOf("heavy cream", "double cream", "whipping cream"), 345.0, 2.1f, 2.8f, 37.0f),
        FoodDbEntry("Almond Butter", listOf("almond butter"), 614.0, 21.0f, 19.0f, 56.0f),
        FoodDbEntry("Cashew Butter", listOf("cashew butter"), 587.0, 18.0f, 28.0f, 49.0f),
        FoodDbEntry("Ground Turkey", listOf("ground turkey", "minced turkey", "turkey mince"), 189.0, 22.0f, 0.0f, 11.0f),
        FoodDbEntry("Ground Pork", listOf("ground pork", "minced pork", "pork mince"), 263.0, 18.5f, 0.0f, 21.0f),
        FoodDbEntry("Lamb Chop", listOf("lamb chop", "lamb loin", "lamb rack", "lamb"), 294.0, 25.0f, 0.0f, 21.0f),
        FoodDbEntry("Lobster", listOf("lobster", "cooked lobster"), 89.0, 19.0f, 0.0f, 0.9f),
        FoodDbEntry("Crab", listOf("crab", "crab meat"), 97.0, 19.0f, 0.0f, 1.5f),
        FoodDbEntry("Mackerel", listOf("mackerel"), 205.0, 19.0f, 0.0f, 14.0f),
        FoodDbEntry("Sardines", listOf("sardines", "canned sardines", "sardines in oil"), 208.0, 25.0f, 0.0f, 11.0f),
        FoodDbEntry("Popcorn", listOf("popcorn", "salted popcorn", "air-popped popcorn"), 387.0, 12.9f, 77.9f, 4.5f),
        FoodDbEntry("Potato Chips", listOf("potato chips", "chips", "crisps"), 536.0, 7.0f, 53.0f, 35.0f),
        FoodDbEntry("Cane Sugar", listOf("sugar", "white sugar", "brown sugar", "granulated sugar"), 387.0, 0.0f, 100.0f, 0.0f)
    )

    fun findClosestFood(query: String): FoodDbEntry? {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isEmpty()) return null

        // 1. Exact matches first (case-insensitive) on name or any synonym
        for (entry in foods) {
            if (entry.name.lowercase() == cleanQuery) {
                return entry
            }
            for (synonym in entry.synonyms) {
                if (synonym.lowercase() == cleanQuery) {
                    return entry
                }
            }
        }

        // 2. Substring matches
        var bestEntry: FoodDbEntry? = null
        var bestScore = -1.0

        for (entry in foods) {
            val candidates = listOf(entry.name) + entry.synonyms
            for (candidate in candidates) {
                val cleanCand = candidate.lowercase()
                val isSubstring = cleanCand.contains(cleanQuery) || cleanQuery.contains(cleanCand)
                if (isSubstring) {
                    val lenRatio = if (cleanCand.length > cleanQuery.length) {
                        cleanQuery.length.toDouble() / cleanCand.length.toDouble()
                    } else {
                        cleanCand.length.toDouble() / cleanQuery.length.toDouble()
                    }
                    val wordMatchBoost = if (hasWordMatch(cleanCand, cleanQuery)) 0.2 else 0.0
                    val score = lenRatio + wordMatchBoost
                    if (score > bestScore) {
                        bestScore = score
                        bestEntry = entry
                    }
                }
            }
        }

        // 3. Fallback fuzzy distance match (Levenshtein)
        if (bestEntry == null) {
            var bestDistance = Int.MAX_VALUE
            for (entry in foods) {
                val candidates = listOf(entry.name) + entry.synonyms
                for (candidate in candidates) {
                    val cleanCand = candidate.lowercase()
                    val distance = levenshtein(cleanQuery, cleanCand)
                    val threshold = (cleanQuery.length * 0.3).coerceAtLeast(2.0).toInt()
                    if (distance <= threshold && distance < bestDistance) {
                        bestDistance = distance
                        bestEntry = entry
                    }
                }
            }
        }

        return bestEntry
    }

    private fun hasWordMatch(s1: String, s2: String): Boolean {
        val words1 = s1.split(Regex("[^a-zA-Z0-9]")).filter { it.isNotEmpty() }
        val words2 = s2.split(Regex("[^a-zA-Z0-9]")).filter { it.isNotEmpty() }
        return words1.any { w1 -> words2.any { w2 -> w1 == w2 } }
    }

    private fun levenshtein(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length

        val dp = IntArray(s2.length + 1) { it }
        for (i in 1..s1.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..s2.length) {
                val temp = dp[j]
                if (s1[i - 1] == s2[j - 1]) {
                    dp[j] = prev
                } else {
                    dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + 1)
                }
                prev = temp
            }
        }
        return dp[s2.length]
    }
}
