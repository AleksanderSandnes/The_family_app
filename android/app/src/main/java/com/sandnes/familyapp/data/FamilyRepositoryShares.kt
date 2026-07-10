package com.sandnes.familyapp.data

import com.sandnes.familyapp.data.remote.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/*
 * FamilyRepository operations for the iOS-parity features that are cross-family or relational:
 * directional family relations and wishlist share links. Kept as extension functions in their own
 * file (mirroring iOS's FamilyRepository+Family / +Wishlist split) so the core class stays focused.
 * These touch only SupabaseManager, so no access to the repository's private state.
 */

// ---- Family relations (directional; each viewer sets their own perspective) ----

/** All relation rows in the family (RLS returns the whole family's rows). */
suspend fun FamilyRepository.getFamilyRelations(familyId: String): List<FamilyRelationModel> =
    runCatching {
        SupabaseManager.client.postgrest
            .from("family_relations")
            .select { filter { eq("family_id", familyId) } }
            .decodeList<FamilyRelationModel>()
    }.getOrDefault(emptyList())

/** Upserts MY relation label to [toUserId] (RLS enforces from_user_id = me). Blank clears it. */
suspend fun FamilyRepository.setFamilyRelation(
    fromUserId: String,
    toUserId: String,
    familyId: String,
    relation: String,
): Result<Unit> =
    runCatching {
        val client = SupabaseManager.client
        if (relation.isBlank()) {
            client.postgrest.from("family_relations").delete {
                filter {
                    eq("from_user_id", fromUserId)
                    eq("to_user_id", toUserId)
                }
            }
        } else {
            client.postgrest.from("family_relations").upsert(
                buildJsonObject {
                    put("from_user_id", fromUserId)
                    put("to_user_id", toUserId)
                    put("family_id", familyId)
                    put("relation", relation.trim())
                    put("updated_at", Instant.now().toString())
                },
            ) { onConflict = "from_user_id,to_user_id" }
        }
        Unit
    }

// ---- Wishlist share links (cross-family, one redeeming user) ----

/** Mints (or returns the existing) share token for a wishlist the caller owns. */
suspend fun FamilyRepository.ensureWishlistShareToken(wishlistId: String): Result<String> =
    runCatching {
        SupabaseManager.client.postgrest
            .rpc("ensure_wishlist_share_token", buildJsonObject { put("p_wishlist_id", wishlistId) })
            .decodeAsOrNull<String>()
            ?: error("Could not create share link")
    }

/** Redeems a share token → grants cross-family read access → returns the wishlist id (or null). */
suspend fun FamilyRepository.acceptWishlistShare(token: String): String? =
    runCatching {
        SupabaseManager.client.postgrest
            .rpc("accept_wishlist_share", buildJsonObject { put("p_token", token) })
            .decodeAsOrNull<String>()
    }.getOrNull()

/** Wishlists shared with me via a redeemed link (cross-family), flagged [WishlistModel.sharedWithMe]. */
suspend fun FamilyRepository.getSharedWishlists(): List<WishlistModel> =
    runCatching {
        val client = SupabaseManager.client
        val wishlistIds =
            client.postgrest
                .from("wishlist_shares")
                .select()
                .decodeList<WishlistShareModel>()
                .map { it.wishlistId }
        if (wishlistIds.isEmpty()) return@runCatching emptyList()
        client.postgrest
            .from("wishlists")
            .select { filter { isIn("id", wishlistIds) } }
            .decodeList<WishlistModel>()
            .map { it.copy(sharedWithMe = true) }
    }.getOrDefault(emptyList())
