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
    private var isAiBotMode = false
    private var currentQuestionIndex = 0
    private var score = 0
    private var timer: CountDownTimer? = null
    private var resultListener: ListenerRegistration? = null
    private var readyListener: ListenerRegistration? = null
    private val botHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private data class Question(val text: String, val options: List<String>, val correctIndex: Int, val domain: String = "General")

    private val allQuestions = listOf(
        Question("Which company developed Kotlin?", listOf("Google", "Oracle", "JetBrains", "Microsoft"), 2, "Kotlin"),
        Question("Which keyword declares a read-only variable in Kotlin?", listOf("var", "val", "const", "final"), 1, "Kotlin"),
        Question("What is the default visibility modifier in Kotlin?", listOf("private", "protected", "public", "internal"), 2, "Kotlin"),
        Question("Which data structure uses LIFO (Last In First Out)?", listOf("Queue", "Stack", "Linked List", "Tree"), 1, "Data Structures"),
        Question("What is the time complexity of searching in a Hash Map?", listOf("O(n)", "O(log n)", "O(1)", "O(n^2)"), 2, "Data Structures"),
        Question("Which algorithm finds the shortest path in a weighted graph?", listOf("BFS", "DFS", "Dijkstra's", "Binary Search"), 2, "Data Structures"),
        Question("How do you start a comment in Python?", listOf("//", "/*", "#", "<!--"), 2, "Python"),
        Question("Which data type is immutable in Python?", listOf("List", "Dictionary", "Set", "Tuple"), 3, "Python"),
        Question("What does HTML stand for?", listOf("Hyper Text Markup Language", "High Tech Multi Language", "Hyper Tabular Main Links", "Home Tool Markup Language"), 0, "Web Dev"),
        Question("Which CSS property changes the background color?", listOf("color", "bgcolor", "background-color", "bg"), 2, "Web Dev"),
        Question("What does XML stand for?", listOf("Extensible Markup Language", "Extra Modern Link", "Extended Multi Language", "Example Markup Language"), 0, "General")
    )

    private var questions = listOf<Question>()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityDuelQuizBinding.inflate(layoutInflater)
        setContentView(binding.root)

        duelId = intent.getStringExtra("duelId") ?: "bot_duel_${System.currentTimeMillis()}"
        partnerId = intent.getStringExtra("partnerId") ?: "uniai_bot"
        chatId = intent.getStringExtra("chatId") ?: ""
        messageId = intent.getStringExtra("messageId") ?: ""
        isHost = intent.getBooleanExtra("isHost", true)
        val subject = intent.getStringExtra("subject") ?: "General"

        questions = allQuestions.filter { it.domain == subject || subject == "General" }
            .shuffled()
            .take(5)

        if (questions.isEmpty()) {
            questions = allQuestions.shuffled().take(5)
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Battle for UniCoins"

        if (isHost && duelId.isNotEmpty() && !duelId.startsWith("bot_")) {
            db.collection("duels").document(duelId).update("status", "active")
        }

        setupReadyLogic()
    }

    private fun setupReadyLogic() {
        val uid = auth.currentUser?.uid ?: return

        binding.btnStartSession.setOnClickListener {
            HapticUtils.playSelection(it)
            binding.btnStartSession.isEnabled = false
            binding.tvReadyStatus.text = "Initializing Quiz Battle..."

            if (duelId.startsWith("bot_") || partnerId == "uniai_bot") {
                isAiBotMode = true
                binding.readyLayout.visibility = View.GONE
                startQuiz()
            } else {
                db.collection("duels").document(duelId).update("ready_$uid", true)
                botHandler.postDelayed({
                    if (!isFinishing && !isDestroyed && binding.readyLayout.visibility == View.VISIBLE) {
                        isAiBotMode = true
                        binding.readyLayout.visibility = View.GONE
                        startQuiz()
                    }
                }, 3000)
            }
        }

        if (!duelId.startsWith("bot_")) {
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
                    botHandler.removeCallbacksAndMessages(null)
                    binding.readyLayout.visibility = View.GONE
                    startQuiz()
                }
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
        timer = object : CountDownTimer(12000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                binding.tvTimer.text = "${millisUntilFinished / 1000}s"
                if (millisUntilFinished < 4000) {
                    HapticUtils.playSelection(binding.tvTimer)
                    binding.tvTimer.setTextColor(android.graphics.Color.RED)
                } else {
                    binding.tvTimer.setTextColor(android.graphics.Color.WHITE)
                }
            }

            override fun onFinish() {
                handleAnswer(-1)
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

        if (isAiBotMode) {
            val botScore = (2..4).random()
            determineOutcome(score.toLong(), botScore.toLong())
        } else {
            val result = mapOf(
                "score_$uid" to score,
                "finished_$uid" to true
            )
            db.collection("duels").document(duelId).set(result, SetOptions.merge())
            listenForFinalResults()
        }
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

        if (!duelId.startsWith("bot_")) {
            db.collection("duels").document(duelId).update("status", "finished")
            if (chatId.isNotEmpty() && messageId.isNotEmpty()) {
                com.example.unilink.repository.ChatRepository().updateDuelStatus(chatId, messageId, "finished")
            }
        }

        when {
            myScore > partnerScore -> {
                updateCoins(myUid, 30, "Won Skill Duel ($myScore vs $partnerScore)! +30 UniCoins 🏆")
            }
            myScore < partnerScore -> {
                updateCoins(myUid, 10, "Skill Duel Finished ($myScore vs $partnerScore). +10 UniCoins participation ⚡")
            }
            else -> {
                updateCoins(myUid, 15, "Duel Draw ($myScore vs $partnerScore). +15 UniCoins 🤝")
            }
        }
    }

    private fun updateCoins(uid: String, amount: Int, msg: String) {
        db.runTransaction { transaction ->
            val ref = db.collection("users").document(uid)
            val user = transaction.get(ref)
            val current = user.getLong("uniCoins") ?: 0
            val xp = user.getLong("totalXp") ?: 0
            transaction.update(ref, "uniCoins", current + amount, "totalXp", xp + 20)
        }.addOnSuccessListener {
            if (!isFinishing && !isDestroyed) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        botHandler.removeCallbacksAndMessages(null)
        resultListener?.remove()
        readyListener?.remove()
    }
}
