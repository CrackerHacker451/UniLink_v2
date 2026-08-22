package com.example.unilink.activities

import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.unilink.databinding.ActivityDuelQuizBinding
import com.example.unilink.utils.ThemeUtils
import com.example.unilink.utils.HapticUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

class DuelQuizActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDuelQuizBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private var duelId = ""
    private var partnerId = ""
    private var chatId = ""
    private var messageId = ""
    private var isHost = false
    private var currentQuestionIndex = 0
    private var score = 0
    private var timer: CountDownTimer? = null
    private var resultListener: ListenerRegistration? = null
    private var readyListener: ListenerRegistration? = null

    private data class Question(val text: String, val options: List<String>, val correctIndex: Int, val domain: String = "General")

    private val allQuestions = listOf(
        // Kotlin
        Question("Which company developed Kotlin?", listOf("Google", "Oracle", "JetBrains", "Microsoft"), 2, "Kotlin"),
        Question("Which keyword is used to declare a read-only variable in Kotlin?", listOf("var", "val", "const", "final"), 1, "Kotlin"),
        Question("What is the default visibility modifier in Kotlin?", listOf("private", "protected", "public", "internal"), 2, "Kotlin"),
        
        // Data Structures
        Question("Which data structure uses LIFO (Last In First Out)?", listOf("Queue", "Stack", "Linked List", "Tree"), 1, "Data Structures"),
        Question("What is the time complexity of searching in a Hash Map (average case)?", listOf("O(n)", "O(log n)", "O(1)", "O(n^2)"), 2, "Data Structures"),
        Question("Which algorithm is used to find the shortest path in a weighted graph?", listOf("BFS", "DFS", "Dijkstra's", "Binary Search"), 2, "Data Structures"),
        
        // Python
        Question("How do you start a comment in Python?", listOf("//", "/*", "#", "<!--"), 2, "Python"),
        Question("Which data type is immutable in Python?", listOf("List", "Dictionary", "Set", "Tuple"), 2, "Python"),
        
        // Web Dev
        Question("What does HTML stand for?", listOf("Hyper Text Markup Language", "High Tech Multi Language", "Hyper Tabular Main Links", "Home Tool Markup Language"), 0, "Web Dev"),
        Question("Which CSS property is used to change the background color?", listOf("color", "bgcolor", "background-color", "bg"), 2, "Web Dev"),
        Question("What is the purpose of the 'git push' command?", listOf("Download code", "Upload code", "Delete code", "Merge code"), 1, "Web Dev"),
        
        // UI/UX
        Question("What does the 'U' in UI stand for?", listOf("Unique", "User", "Universal", "Utility"), 1, "UI/UX"),
        Question("Which color is typically used to represent a successful action?", listOf("Red", "Blue", "Green", "Yellow"), 2, "UI/UX"),
        
        // Android/General
        Question("What does XML stand for?", listOf("Extensible Markup Language", "Extra Modern Link", "Extended Multi Language", "Example Markup Language"), 0, "General"),
        Question("Which Firestore field type is used for dates?", listOf("String", "Long", "Timestamp", "Object"), 2, "General")
    )

    private var questions = listOf<Question>()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityDuelQuizBinding.inflate(layoutInflater)
        setContentView(binding.root)

        duelId = intent.getStringExtra("duelId") ?: ""
        partnerId = intent.getStringExtra("partnerId") ?: ""
        chatId = intent.getStringExtra("chatId") ?: ""
        messageId = intent.getStringExtra("messageId") ?: ""
        isHost = intent.getBooleanExtra("isHost", false)
        val subject = intent.getStringExtra("subject") ?: "General"

        // Filter and shuffle questions
        questions = allQuestions.filter { it.domain == subject || subject == "General" }
            .shuffled()
            .take(5)
        
        if (questions.isEmpty()) {
            questions = allQuestions.shuffled().take(5)
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Battle for UniCoins"

        if (isHost) {
            db.collection("duels").document(duelId).update("status", "active")
        }

        setupReadyLogic()
    }

    private fun setupReadyLogic() {
        val uid = auth.currentUser?.uid ?: return
        
        binding.btnStartSession.setOnClickListener {
            HapticUtils.playSelection(it)
            binding.btnStartSession.isEnabled = false
            binding.tvReadyStatus.text = "Waiting for partner..."
            db.collection("duels").document(duelId).update("ready_$uid", true)
        }

        readyListener = db.collection("duels").document(duelId).addSnapshotListener { snapshot, _ ->
            if (isFinishing || isDestroyed) return@addSnapshotListener
            val doc = snapshot ?: return@addSnapshotListener
            
            val hostId = doc.getString("hostId") ?: ""
            val receiverId = doc.getString("receiverId") ?: ""
            
            val hostReady = doc.getBoolean("ready_$hostId") ?: false
            val receiverReady = doc.getBoolean("ready_$receiverId") ?: false
            
            if (hostReady && receiverReady) {
                readyListener?.remove()
                readyListener = null
                binding.readyLayout.visibility = View.GONE
                startQuiz()
            }
        }
    }

    private fun startQuiz() {
        if (currentQuestionIndex < questions.size) {
            val q = questions[currentQuestionIndex]
            binding.tvQuestionNum.text = "QUESTION ${currentQuestionIndex + 1} OF ${questions.size}"
            binding.tvQuestionText.text = q.text
            binding.quizProgress.progress = currentQuestionIndex + 1
            
            val buttons = listOf(binding.btnOpt1, binding.btnOpt2, binding.btnOpt3, binding.btnOpt4)
            buttons.forEachIndexed { index, btn ->
                btn.text = q.options[index]
                btn.isEnabled = true
                btn.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                btn.setOnClickListener {
                    handleAnswer(index)
                }
            }
            
            startTimer()
        } else {
            finishQuiz()
        }
    }

    private fun startTimer() {
        timer?.cancel()
        timer = object : CountDownTimer(15000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                binding.tvTimer.text = "${millisUntilFinished / 1000}s"
                if (millisUntilFinished < 5000) {
                    HapticUtils.playSelection(binding.tvTimer)
                    binding.tvTimer.setTextColor(android.graphics.Color.RED)
                } else {
                    binding.tvTimer.setTextColor(android.graphics.Color.WHITE)
                }
            }

            override fun onFinish() {
                handleAnswer(-1) // Timeout
            }
        }.start()
    }

    private fun handleAnswer(index: Int) {
        timer?.cancel()
        val correctIndex = questions[currentQuestionIndex].correctIndex
        if (index == correctIndex) {
            score++
            HapticUtils.playSuccess(binding.root)
        } else {
            HapticUtils.playHeavy(binding.root)
        }
        
        currentQuestionIndex++
        startQuiz()
    }

    private fun finishQuiz() {
        binding.waitingLayout.visibility = View.VISIBLE
        val uid = auth.currentUser?.uid ?: return
        
        // Upload result
        val result = mapOf(
            "score_$uid" to score,
            "finished_$uid" to true
        )
        
        db.collection("duels").document(duelId).set(result, SetOptions.merge())
        listenForFinalResults()
    }

    private fun listenForFinalResults() {
        resultListener = db.collection("duels").document(duelId).addSnapshotListener { snapshot, _ ->
            if (isFinishing || isDestroyed) return@addSnapshotListener
            val doc = snapshot ?: return@addSnapshotListener
            
            val hostFinished = doc.getBoolean("finished_${doc.getString("hostId")}") ?: false
            val receiverFinished = doc.getBoolean("finished_${doc.getString("receiverId")}") ?: false
            
            if (hostFinished && receiverFinished) {
                resultListener?.remove()
                val hostScore = doc.getLong("score_${doc.getString("hostId")}") ?: 0
                val receiverScore = doc.getLong("score_${doc.getString("receiverId")}") ?: 0
                val myUid = auth.currentUser?.uid
                
                val myScore = if (myUid == doc.getString("hostId")) hostScore else receiverScore
                val partnerScore = if (myUid == doc.getString("hostId")) receiverScore else hostScore
                
                determineOutcome(myScore, partnerScore)
            }
        }
    }

    private fun determineOutcome(myScore: Long, partnerScore: Long) {
        val myUid = auth.currentUser?.uid ?: return
        
        // Mark as finished once results are in
        db.collection("duels").document(duelId).update("status", "finished")
        if (chatId.isNotEmpty() && messageId.isNotEmpty()) {
            com.example.unilink.repository.ChatRepository().updateDuelStatus(chatId, messageId, "finished")
        }

        when {
            myScore > partnerScore -> {
                // Winner: +40 UniCoins
                updateCoins(myUid, 40, "Won Skill Duel! +40 coins")
            }
            myScore < partnerScore -> {
                // Loser: -50 UniCoins
                updateCoins(myUid, -50, "Lost Skill Duel. -50 coins")
            }
            else -> {
                // Draw: -5 coins entry fee
                updateCoins(myUid, -5, "Duel Draw. -5 entry fee")
            }
        }
    }

    private fun updateCoins(uid: String, amount: Int, msg: String) {
        db.runTransaction { transaction ->
            val ref = db.collection("users").document(uid)
            val user = transaction.get(ref)
            val current = user.getLong("uniCoins") ?: 0
            transaction.update(ref, "uniCoins", current + amount)
        }.addOnSuccessListener {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        resultListener?.remove()
    }
}
