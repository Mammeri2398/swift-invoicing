# Swift Invoicing: Gemini script for the launch post and ad visuals

How to use it:

1. Open Gemini (gemini.google.com), start a new chat.
2. Optional but recommended: attach `screenshots/dashboard.jpg`, `screenshots/invoice-page.jpg` and
   `screenshots/sample-invoice.pdf` so Gemini sees the real product (all data in them is fictional).
3. Fill in the `[...]` placeholders below (price, phone, link…), then paste **PROMPT 1**.
4. When the texts are done, paste **PROMPT 2** in the same chat to get the images.

Every feature listed here was checked end to end on 2026-11-08 (197 automated E2E checks + 380 unit
tests, all passing). Don't let Gemini add features that aren't on this list.

---

## PROMPT 1: the posts (copy everything between the lines)

---

You are a senior social-media copywriter for the Algerian market. You write in French and in Modern
Standard Arabic (with a light Algerian touch where it feels natural), for Facebook, Instagram and
LinkedIn. Your goal: **sell licences of a software product** to Algerian auto-entrepreneurs
(holders of the ANAE card), freelancers and very small businesses.

### The product

**Swift Invoicing** (by Swift Labs) is invoicing software made for Algerian auto-entrepreneurs and
small businesses. It is installed on the customer's own Windows PC (one installer, nothing else to
set up): it works **without internet** and the data stays **at the customer's place**, not in a
foreign cloud.

What it does (only use these, never invent others):

- **Compliant invoices in a few clicks**: numbering `FA-2026-0001` that is continuous and has no
  gaps (it restarts each year), the amount written out in words ("cent quinze mille dinars
  algériens"), the mention « TVA non applicable » for auto-entrepreneurs, the ANAE card number, NIF
  and NIS, logo, and a stamp-and-signature box. Amounts in DZD, formatted `1 234 567,50 DA`.
- **Professional PDF** ready to print or send, including client names and addresses **in Arabic**,
  which print correctly.
- **Send by email** with the PDF attached, straight from the invoice.
- **Quotes and pro forma invoices** → accepted / refused → **turned into an invoice in one click**.
- **Credit notes (avoirs)**, cancellation (the number is kept, never reused), invoices locked once
  issued (no after-the-fact edits).
- **Payment tracking**: partial payments (cash, transfer, cheque, CCP), remaining balance, "paid"
  status, overdue invoices.
- **Recurring invoices** (rent, maintenance, subscriptions): the draft gets prepared on its own,
  monthly / quarterly / half-yearly / yearly.
- **Annual ceiling gauge**: turnover for the year, month by month, and warnings at 80% and 100% of
  the auto-entrepreneur ceiling (the amount can be changed in the settings).
- **For the accountant**: CSV and Excel exports, accounting journal, VAT report (for companies),
  statement for each client.
- **Client and product/service catalogue**, history of each invoice (who created, issued, paid,
  sent it, and when).
- **Several companies in a single installation**, users with roles and permissions,
  **two-factor authentication**, automatic **backups** and one-click restore, activity log.
- **Public invoice check**: your client can confirm an invoice number is genuine without seeing any
  amount.
- Interface in **French, English and Arabic** (right-to-left), works on phone, tablet and computer
  on the local network.

Price / offer: **[PRICE IN DA, e.g. one-time licence of X DA or X DA/year]**,
**[OFFER, e.g. -20% until DD/MM, free installation, free demo]**
Contact: **[PHONE / WHATSAPP]** · **[EMAIL]** · **[FACEBOOK PAGE / WEBSITE LINK]**
City / area covered: **[CITY, e.g. Algiers, delivery and installation all over Algeria]**

### Strict rules

- Never claim an official endorsement or partnership with the ANAE, the DGI or any government
  body. Write "conçu pour les auto-entrepreneurs titulaires de la carte ANAE", never
  "agréé ANAE" or "certifié DGI".
- Don't invent numbers (customers, savings, ratings, testimonials). If a testimonial fits, write
  `[TÉMOIGNAGE CLIENT À INSÉRER]`.
- No cloud, AI or e-invoicing-platform claims: the software is installed locally.
- Warm, confident, concrete tone. Short sentences. Emojis in moderation (Facebook/Instagram only).

### What I want back

1. **Main Facebook post in French** (180 to 250 words): a hook about the auto-entrepreneur's real
   pain (handwritten or Excel invoices, numbering mistakes, fear of going over the ceiling, the
   accountant asking for statements), then 6 to 8 key benefits as a list with emojis, the offer,
   and a clear call to action (DM / WhatsApp / free demo).
2. **The same post in Arabic** (adapted, not word-for-word), to publish as a separate post.
3. **Instagram caption** in French (shorter, 80 to 120 words) + 15 relevant hashtags
   (French/Arabic mix, e.g. #autoentrepreneur #ANAE #Algérie #facturation #DZ #مقاول_ذاتي).
4. **LinkedIn post** in French, more professional (150 to 200 words), for accountants and small
   business owners, with no emojis or almost none.
5. **3 short ad variants** (Facebook/Instagram Ads), each with: headline (max 40 characters),
   main text (max 125 characters), description (max 30 characters), button suggestion.
6. **Script for a 30-second Reel/Story**: 5 to 6 scenes, on-screen text + voice-over in French,
   ending with the call to action.
7. **5 headline ideas** to put on the visual (max 6 words each), in French and in Arabic.

Present everything with clear headings, ready to copy and paste.

---

## PROMPT 2: the images (paste it after the posts)

---

Now generate the **advertising visuals** for Swift Invoicing, using the attached screenshots as
a reference for the real interface if I gave them to you.

Shared art direction:

- Style: modern, clean, professional "fintech / SaaS", trustworthy. Plenty of white space.
- Colours: deep ocean blue **#004B6F** (main), light blue **#D7ECFF**, white, with a single touch
  of warm accent (soft gold or green for "payée / paid").
- Scene: a laptop (and optionally a phone) showing an invoicing dashboard with a big amount in
  "DA", a circular gauge "8,2 %" and a list of invoices, on a bright office desk. Next to it, a
  printed A4 invoice with the title "Facture FA-2026-0001" and a stamp. An Algerian atmosphere
  through subtle details (light from the Algiers bay or white architecture blurred in the
  background, a glass of mint tea), never clichéd.
- People (optional): a young Algerian entrepreneur, smiling and confident, business casual outfit.
- Text in the image: **only** the name "Swift Invoicing" and ONE short headline taken from the
  ideas above (e.g. « Vos factures, enfin simples. »). Keep a clear area for the text, and spell it
  exactly. No other text, no fake logos, **no ANAE, DGI or Algerian state logos or emblems**, no
  real brands on the devices.
- Leave space for a strip at the bottom: "[PHONE] · [LINK]" (I'll add it myself if the text comes
  out badly).

Formats to generate:

1. **Square 1:1** (1080×1080) for the Facebook/Instagram feed: laptop + printed invoice, headline in
   French.
2. **Vertical 4:5** (1080×1350) for Instagram: the entrepreneur with their phone, a notification
   "Facture payée ✓ 36 000,00 DA", headline in French.
3. **Story 9:16** (1080×1920): very simple, the dashboard in large on a phone, headline at the top,
   big empty area at the bottom for the button.
4. **Arabic version of the square visual**: same composition, headline in Arabic (right-to-left
   layout), "Swift Invoicing" stays in Latin letters.
5. **Facebook cover banner** (1640×624): a wide composition, product on the right, empty area on the
   left for the slogan.

If a piece of text comes out with spelling mistakes, regenerate that image with no text at all; I'll
add the text in Canva.

---

## Tips

- Gemini sometimes misspells text inside images: ask for "no text" versions and add the headline
  and the price in Canva or CapCut, which is faster and looks cleaner.
- For a more convincing visual, the real screenshots (`screenshots/`) can be placed in a laptop or
  phone mockup in Canva, instead of an AI-generated interface.
- Before publishing, check the price, the contact details and that no visual shows an official
  logo.
