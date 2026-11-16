package sg.edumate.app.ui.privacy

/** Versioned privacy notice. Bump [VERSION] when wording changes; users are asked to re-acknowledge. */
object PrivacyNotice {
    const val VERSION = 1

    val sections = listOf(
        "What we collect" to "Your name and school email (to sign you in), the lesson plans and worksheets you create, class lists " +
            "(pseudonymised by default) and live-session responses. We do not collect NRIC/FIN numbers, student contact details, " +
            "location or contacts.",
        "Why we collect it" to "Only to help you plan, teach and track learning for your classes. We do not sell data or use it for advertising.",
        "AI processing" to "Before any request is sent to the AI service, names, NRIC/FIN, emails and phone numbers are replaced with " +
            "placeholders on your device. AI output is labelled and must be reviewed by you before students see it. " +
            "Each AI request is recorded in an audit log (without content).",
        "Where it is stored" to "On this device in an encrypted database, and on EduMate servers in Singapore (asia-southeast1), " +
            "encrypted in transit (TLS 1.2+) and at rest.",
        "How long we keep it" to "Live-session responses: 30 days. Unreviewed AI drafts: 90 days. Class lists: 180 days after the class ends. " +
            "Your school may set shorter periods.",
        "Your rights" to "You can view, export, correct or delete your data at any time from this screen, and withdraw consent for AI or " +
            "analytics. Students and parents can make requests through the school's Data Protection Officer.",
        "Data breaches" to "If a breach is likely to cause significant harm, we notify your school and the PDPC within 3 calendar days of " +
            "assessing it, and affected individuals as required.",
    )
}
