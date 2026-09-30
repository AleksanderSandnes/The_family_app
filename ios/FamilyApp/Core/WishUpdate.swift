/// Editable wish fields. Nil clears an optional value in the database.
struct WishUpdate {
    let text: String
    let link: String?
    let price: String?
    let imageUrl: String?
    let description: String?
}
