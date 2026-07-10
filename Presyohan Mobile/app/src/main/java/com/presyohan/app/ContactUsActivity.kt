package com.presyohan.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import kotlinx.coroutines.launch

class ContactUsActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var loadingOverlay: View

    // CEO details
    private lateinit var imgCeoPhoto: ImageView

    // Contact cards
    private lateinit var cardLocation: View
    private lateinit var cardEmail: View
    private lateinit var cardPhone: View
    private lateinit var txtLocation: TextView
    private lateinit var txtEmail: TextView
    private lateinit var txtPhone: TextView

    // Ratings UI
    private lateinit var layoutRatingEditable: View
    private lateinit var layoutRatingDone: View
    private lateinit var txtRatingEditTitle: TextView
    private lateinit var imgStar1: ImageView
    private lateinit var imgStar2: ImageView
    private lateinit var imgStar3: ImageView
    private lateinit var imgStar4: ImageView
    private lateinit var imgStar5: ImageView
    private lateinit var edtRatingReason: EditText
    private lateinit var btnSubmitRating: AppCompatButton

    // Done rating display views
    private lateinit var imgDoneStar1: ImageView
    private lateinit var imgDoneStar2: ImageView
    private lateinit var imgDoneStar3: ImageView
    private lateinit var imgDoneStar4: ImageView
    private lateinit var imgDoneStar5: ImageView
    private lateinit var txtDoneRatingReason: TextView
    private lateinit var btnEditRating: View
    private lateinit var doneStarsList: List<ImageView>

    // Comments Input
    private lateinit var layoutReplyContext: LinearLayout
    private lateinit var txtReplyingTo: TextView
    private lateinit var btnCancelReply: ImageView
    private lateinit var edtComment: EditText
    private lateinit var btnSendComment: AppCompatButton

    // Comments RecyclerView
    private lateinit var rvComments: RecyclerView
    private lateinit var commentsAdapter: ContactCommentAdapter

    // State variables
    private var selectedRating = 0
    private var isEditMode = false
    private var currentContactLocation = "Curva Medellin, Cebu City, Philippines"
    private var currentContactEmail = "presyohan@gmail.com"
    private var currentContactPhone = "+639 430 8387"
    private var replyParentId: String? = null

    private lateinit var starsList: List<ImageView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_us)

        // Attach loading overlay
        loadingOverlay = LoadingOverlayHelper.attach(this)

        // Initialize Views
        btnBack = findViewById(R.id.btnBack)
        imgCeoPhoto = findViewById(R.id.imgCeoPhoto)

        cardLocation = findViewById(R.id.cardLocation)
        cardEmail = findViewById(R.id.cardEmail)
        cardPhone = findViewById(R.id.cardPhone)
        txtLocation = findViewById(R.id.txtLocation)
        txtEmail = findViewById(R.id.txtEmail)
        txtPhone = findViewById(R.id.txtPhone)

        layoutRatingEditable = findViewById(R.id.layoutRatingEditable)
        layoutRatingDone = findViewById(R.id.layoutRatingDone)
        txtRatingEditTitle = findViewById(R.id.txtRatingEditTitle)

        imgStar1 = findViewById(R.id.imgStar1)
        imgStar2 = findViewById(R.id.imgStar2)
        imgStar3 = findViewById(R.id.imgStar3)
        imgStar4 = findViewById(R.id.imgStar4)
        imgStar5 = findViewById(R.id.imgStar5)
        edtRatingReason = findViewById(R.id.edtRatingReason)
        btnSubmitRating = findViewById(R.id.btnSubmitRating)

        imgDoneStar1 = findViewById(R.id.imgDoneStar1)
        imgDoneStar2 = findViewById(R.id.imgDoneStar2)
        imgDoneStar3 = findViewById(R.id.imgDoneStar3)
        imgDoneStar4 = findViewById(R.id.imgDoneStar4)
        imgDoneStar5 = findViewById(R.id.imgDoneStar5)
        txtDoneRatingReason = findViewById(R.id.txtDoneRatingReason)
        btnEditRating = findViewById(R.id.btnEditRating)

        layoutReplyContext = findViewById(R.id.layoutReplyContext)
        txtReplyingTo = findViewById(R.id.txtReplyingTo)
        btnCancelReply = findViewById(R.id.btnCancelReply)
        edtComment = findViewById(R.id.edtComment)
        btnSendComment = findViewById(R.id.btnSendComment)

        rvComments = findViewById(R.id.rvComments)

        starsList = listOf(imgStar1, imgStar2, imgStar3, imgStar4, imgStar5)
        doneStarsList = listOf(imgDoneStar1, imgDoneStar2, imgDoneStar3, imgDoneStar4, imgDoneStar5)

        // Setup Edit Rating click listener (Transition from Done layout to Editable layout)
        btnEditRating.setOnClickListener {
            layoutRatingDone.visibility = View.GONE
            layoutRatingEditable.visibility = View.VISIBLE
        }

        // Set back action
        btnBack.setOnClickListener {
            finish()
        }

        // Setup CEO avatar circular crop using Coil
        imgCeoPhoto.load(R.drawable.profile_ceo) {
            transformations(CircleCropTransformation())
        }

        // Setup stars click listeners
        starsList.forEachIndexed { index, imageView ->
            imageView.setOnClickListener {
                setRatingUI(index + 1)
            }
        }

        // Setup quick contacts actions
        cardLocation.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(currentContactLocation)))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Maps app not found", Toast.LENGTH_SHORT).show()
            }
        }

        cardEmail.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$currentContactEmail"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Email app not found", Toast.LENGTH_SHORT).show()
            }
        }

        cardPhone.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$currentContactPhone"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Dialer app not found", Toast.LENGTH_SHORT).show()
            }
        }

        // Submit Rating Action
        btnSubmitRating.setOnClickListener {
            submitRating()
        }

        // Cancel Reply Action
        btnCancelReply.setOnClickListener {
            cancelReplyContext()
        }

        // Send Comment Action
        btnSendComment.setOnClickListener {
            postComment()
        }

        // Setup Comments Recycler
        rvComments.layoutManager = LinearLayoutManager(this)
        commentsAdapter = ContactCommentAdapter(
            comments = emptyList(),
            userProfiles = emptyMap(),
            currentUserId = null,
            currentUserRole = null,
            onReplyClicked = { comment, author ->
                setReplyContext(comment, author)
            },
            onDeleteClicked = { comment ->
                confirmDeleteComment(comment)
            }
        )
        rvComments.adapter = commentsAdapter

        // Load initial data
        loadData()
    }

    private fun setRatingUI(rating: Int) {
        selectedRating = rating
        for (i in 0 until 5) {
            if (i < rating) {
                starsList[i].setImageResource(R.drawable.ic_star_filled)
            } else {
                starsList[i].setImageResource(R.drawable.ic_star_empty)
            }
        }
    }

    private fun loadData() {
        loadingOverlay.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                // 1. Load dynamic contact info
                val contactInfo = SupabaseAuthService.getContactInfo()
                if (contactInfo != null) {
                    currentContactLocation = contactInfo.location
                    currentContactEmail = contactInfo.email
                    currentContactPhone = contactInfo.number

                    txtLocation.text = currentContactLocation
                    txtEmail.text = currentContactEmail
                    txtPhone.text = currentContactPhone
                }

                // 2. Load current user's rating (if any)
                val userRating = SupabaseAuthService.getUserRating()
                if (userRating != null) {
                    // Pre-fill editable layout values
                    setRatingUI(userRating.rating)
                    edtRatingReason.setText(userRating.reason ?: "")
                    isEditMode = true
                    btnSubmitRating.text = "Update Rating"
                    txtRatingEditTitle.text = "Update Your Rating"

                    // Fill done layout values
                    for (i in 0 until 5) {
                        if (i < userRating.rating) {
                            doneStarsList[i].setImageResource(R.drawable.ic_star_filled)
                        } else {
                            doneStarsList[i].setImageResource(R.drawable.ic_star_empty)
                        }
                    }
                    txtDoneRatingReason.text = if (userRating.reason.isNullOrBlank()) "No feedback comment provided." else userRating.reason
                    
                    // Show Done layout, Hide Editable layout
                    layoutRatingEditable.visibility = View.GONE
                    layoutRatingDone.visibility = View.VISIBLE
                } else {
                    setRatingUI(0)
                    edtRatingReason.setText("")
                    isEditMode = false
                    btnSubmitRating.text = "Submit Rating"
                    txtRatingEditTitle.text = "How is your experience with Presyohan?"

                    // Show Editable layout, Hide Done layout
                    layoutRatingEditable.visibility = View.VISIBLE
                    layoutRatingDone.visibility = View.GONE
                }

                // 3. Load feedback comments thread
                refreshCommentsList()

            } catch (e: Exception) {
                android.util.Log.e("ContactUs", "Error loading page data", e)
                ReusableDialogHelper.handleNetworkError(this@ContactUsActivity, e) {
                    loadData()
                }
            } finally {
                loadingOverlay.visibility = View.GONE
            }
        }
    }

    private suspend fun refreshCommentsList() {
        val rawMessages = SupabaseAuthService.getContactMessages()
        android.util.Log.d("ContactUs", "Fetched ${rawMessages.size} raw messages from DB")

        val uniqueUserIds = rawMessages.map { it.user_id }.distinct()
        val profiles = SupabaseAuthService.getUserProfiles(uniqueUserIds)
        val profilesMap = profiles.associateBy { it.id }

        val currentUserId = SupabaseAuthService.getCurrentUserId()
        android.util.Log.d("ContactUs", "Current auth UID: $currentUserId")

        // Resolve current user's role — try direct map lookup first, then fall back to direct API call
        val currentUserProfile = if (currentUserId != null) {
            profilesMap[currentUserId]
                ?: profiles.firstOrNull { it.id == currentUserId }
                ?: SupabaseAuthService.getUserProfile()
        } else null
        val currentUserRole = currentUserProfile?.role
        val isCurrentUserAdmin = currentUserRole?.lowercase() == "admin"

        android.util.Log.d("ContactUs", "User role: $currentUserRole, isAdmin: $isCurrentUserAdmin")
        android.util.Log.d("ContactUs", "Messages from current user: ${rawMessages.count { it.user_id == currentUserId }}")

        // Flatten the message hierarchy (filtering to current user's thread unless they are an admin)
        val flattenedList = flattenMessages(rawMessages, currentUserId, isCurrentUserAdmin)
        android.util.Log.d("ContactUs", "Flattened list size: ${flattenedList.size}")
        commentsAdapter.updateData(flattenedList, profilesMap, currentUserId, currentUserRole)
    }

    private fun confirmDeleteComment(comment: ContactMessageRow) {
        ReusableDialogHelper.showCustomDialog(
            context = this,
            title = "Delete Comment",
            message = "Are you sure you want to delete this comment? This will also delete any replies to it.",
            positiveButtonText = "Delete",
            positiveAction = {
                deleteComment(comment)
            },
            negativeButtonText = "Cancel"
        )
    }

    private fun deleteComment(comment: ContactMessageRow) {
        val commentId = comment.id ?: return
        loadingOverlay.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val success = SupabaseAuthService.deleteContactMessage(commentId)
                if (success) {
                    Toast.makeText(this@ContactUsActivity, "Comment deleted successfully", Toast.LENGTH_SHORT).show()
                    refreshCommentsList()
                } else {
                    Toast.makeText(this@ContactUsActivity, "Failed to delete comment", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                ReusableDialogHelper.handleNetworkError(this@ContactUsActivity, e) { deleteComment(comment) }
            } finally {
                loadingOverlay.visibility = View.GONE
            }
        }
    }

    private fun flattenMessages(
        allMessages: List<ContactMessageRow>,
        currentUserId: String?,
        isCurrentUserAdmin: Boolean
    ): List<Pair<ContactMessageRow, Int>> {
        val roots = allMessages.filter {
            it.parent_id == null && (isCurrentUserAdmin || currentUserId == null || it.user_id == currentUserId)
        }
        val result = mutableListOf<Pair<ContactMessageRow, Int>>()

        fun traverse(parentId: String, currentLevel: Int) {
            val children = allMessages.filter { it.parent_id == parentId }
            for (child in children) {
                result.add(Pair(child, currentLevel))
                child.id?.let { traverse(it, currentLevel + 1) }
            }
        }

        for (root in roots) {
            result.add(Pair(root, 0))
            root.id?.let { traverse(it, 1) }
        }

        return result
    }

    private fun submitRating() {
        if (selectedRating == 0) {
            ReusableDialogHelper.showCustomDialog(
                context = this,
                title = "Invalid Rating",
                message = "Please select at least 1 star before submitting.",
                positiveButtonText = "OK"
            )
            return
        }

        loadingOverlay.visibility = View.VISIBLE
        val reason = edtRatingReason.text.toString().trim().ifBlank { null }

        lifecycleScope.launch {
            try {
                val success = SupabaseAuthService.upsertUserRating(selectedRating, reason)
                if (success) {
                    isEditMode = true
                    btnSubmitRating.text = "Update Rating"
                    ClonePricesDialogHelper.showCloneCompleteDialog(
                        context = this@ContactUsActivity,
                        title = "Feedback Saved",
                        message = "Your rating has been submitted successfully. Thank you for your support!",
                        buttonText = "Close"
                    ) {
                        loadData()
                    }
                } else {
                    Toast.makeText(this@ContactUsActivity, "Failed to submit rating", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                ReusableDialogHelper.handleNetworkError(this@ContactUsActivity, e) { submitRating() }
            } finally {
                loadingOverlay.visibility = View.GONE
            }
        }
    }

    private fun setReplyContext(comment: ContactMessageRow, author: AppUserRow?) {
        replyParentId = comment.id
        val authorName = author?.name ?: "User"
        txtReplyingTo.text = "Replying to: $authorName"
        layoutReplyContext.visibility = View.VISIBLE
        edtComment.requestFocus()
    }

    private fun cancelReplyContext() {
        replyParentId = null
        layoutReplyContext.visibility = View.GONE
    }

    private fun postComment() {
        val text = edtComment.text.toString().trim()
        if (text.isEmpty()) return

        loadingOverlay.visibility = View.VISIBLE
        val parentId = replyParentId

        lifecycleScope.launch {
            try {
                val success = SupabaseAuthService.postContactMessage(text, parentId)
                if (success) {
                    edtComment.text.clear()
                    cancelReplyContext()
                    refreshCommentsList()
                } else {
                    Toast.makeText(this@ContactUsActivity, "Failed to post comment", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                ReusableDialogHelper.handleNetworkError(this@ContactUsActivity, e) { postComment() }
            } finally {
                loadingOverlay.visibility = View.GONE
            }
        }
    }
}

class ContactCommentAdapter(
    private var comments: List<Pair<ContactMessageRow, Int>>,
    private var userProfiles: Map<String, AppUserRow>,
    private var currentUserId: String?,
    private var currentUserRole: String?,
    private val onReplyClicked: (ContactMessageRow, AppUserRow?) -> Unit,
    private val onDeleteClicked: (ContactMessageRow) -> Unit
) : RecyclerView.Adapter<ContactCommentAdapter.CommentViewHolder>() {

    class CommentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val indentSpacer: View = view.findViewById(R.id.indentSpacer)
        val imgCommentAvatar: ImageView = view.findViewById(R.id.imgCommentAvatar)
        val txtCommentUserName: TextView = view.findViewById(R.id.txtCommentUserName)
        val txtCommentBadge: TextView = view.findViewById(R.id.txtCommentBadge)
        val txtCommentTime: TextView = view.findViewById(R.id.txtCommentTime)
        val txtCommentMessage: TextView = view.findViewById(R.id.txtCommentMessage)
        val txtCommentReplyAction: TextView = view.findViewById(R.id.txtCommentReplyAction)
        val txtCommentDivider: TextView = view.findViewById(R.id.txtCommentDivider)
        val txtCommentDeleteAction: TextView = view.findViewById(R.id.txtCommentDeleteAction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_contact_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val item = comments[position]
        val comment = item.first
        val indent = item.second

        // 1. Handle nested indentation spacer layout (capping visual indent at level 1 to prevent clutter)
        val maxIndent = 1
        val visualIndent = if (indent > maxIndent) maxIndent else indent
        if (visualIndent > 0) {
            holder.indentSpacer.visibility = View.VISIBLE
            val density = holder.itemView.context.resources.displayMetrics.density
            val widthPx = (visualIndent * 16 * density).toInt()
            holder.indentSpacer.layoutParams = holder.indentSpacer.layoutParams.also {
                it.width = widthPx
            }
        } else {
            holder.indentSpacer.visibility = View.GONE
        }

        // 2. Load User Profile
        val profile = userProfiles[comment.user_id]
        val isAdmin = profile?.role?.lowercase() == "admin"
        if (isAdmin) {
            val spannable = android.text.SpannableStringBuilder("PRESYOHAN")
            val orangeColor = ContextCompat.getColor(holder.itemView.context, R.color.presyo_orange)
            val tealColor = ContextCompat.getColor(holder.itemView.context, R.color.presyo_teal)
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(orangeColor),
                0, 6, // "PRESYO"
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(tealColor),
                6, 9, // "HAN"
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            holder.txtCommentUserName.text = spannable
        } else {
            val displayName = profile?.name?.uppercase() ?: "USER"
            holder.txtCommentUserName.text = displayName
        }

        // Circular avatar crop loading
        holder.imgCommentAvatar.clearColorFilter()
        if (isAdmin) {
            holder.imgCommentAvatar.rotation = -45f
            holder.imgCommentAvatar.load(R.drawable.icon_presyohan) {
                transformations(CircleCropTransformation())
            }
        } else {
            holder.imgCommentAvatar.rotation = 0f
            val avatarUrl = profile?.avatar_url
            if (!avatarUrl.isNullOrBlank()) {
                holder.imgCommentAvatar.load(avatarUrl) {
                    crossfade(true)
                    transformations(CircleCropTransformation())
                    error(R.drawable.avatar_default)
                }
            } else {
                holder.imgCommentAvatar.load(R.drawable.avatar_default) {
                    transformations(CircleCropTransformation())
                }
            }
        }

        // Show ADMIN badge if role is admin
        holder.txtCommentBadge.visibility = View.GONE

        // 3. Set content message
        holder.txtCommentMessage.text = comment.message
        holder.txtCommentTime.text = formatTimeAgo(comment.created_at)

        // 4. Setup reply trigger action
        holder.txtCommentReplyAction.setOnClickListener {
            onReplyClicked(comment, profile)
        }

        // 5. Setup delete trigger action (visible only to authors or admins)
        val isAuthor = comment.user_id == currentUserId
        val isCurrentUserAdmin = currentUserRole?.lowercase() == "admin"
        if (isAuthor || isCurrentUserAdmin) {
            holder.txtCommentDivider.visibility = View.VISIBLE
            holder.txtCommentDeleteAction.visibility = View.VISIBLE
            holder.txtCommentDeleteAction.setOnClickListener {
                onDeleteClicked(comment)
            }
        } else {
            holder.txtCommentDivider.visibility = View.GONE
            holder.txtCommentDeleteAction.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = comments.size

    fun updateData(
        newComments: List<Pair<ContactMessageRow, Int>>,
        newProfiles: Map<String, AppUserRow>,
        uid: String?,
        role: String?
    ) {
        comments = newComments
        userProfiles = newProfiles
        currentUserId = uid
        currentUserRole = role
        notifyDataSetChanged()
    }

    private fun formatTimeAgo(isoString: String?): String {
        if (isoString.isNullOrBlank()) return ""
        try {
            // Parses standard Supabase timestamps
            val raw = isoString.replace("Z", "").substringBefore("+")
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val date = sdf.parse(raw) ?: return ""
            val diffMs = System.currentTimeMillis() - date.time
            val diffSec = diffMs / 1000

            if (diffSec < 60) return "Just now"
            val diffMin = diffSec / 60
            if (diffMin < 60) return "${diffMin}m ago"
            val diffHour = diffMin / 60
            if (diffHour < 24) return "${diffHour}h ago"
            val diffDays = diffHour / 24
            if (diffDays < 7) return "${diffDays}d ago"

            return java.text.SimpleDateFormat("MMM dd", java.util.Locale.US).format(date)
        } catch (e: Exception) {
            android.util.Log.e("ContactCommentAdapter", "Error formatting time: $isoString", e)
            return ""
        }
    }
}
