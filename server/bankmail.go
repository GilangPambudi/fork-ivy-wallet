package main

import (
	"errors"
	"fmt"
	"html"
	"regexp"
	"strconv"
	"strings"
	"time"
)

// Kinds of transactions read from bank emails.
const (
	KindPayment    = "payment"     // money paid to a merchant
	KindTransfer   = "transfer"    // money sent to another account
	KindTopUp      = "topup"       // money moved to an e-wallet
	KindIncoming   = "incoming"    // money received
	KindPocketMove = "pocket_move" // money moved between pockets of one bank
)

// EmailTransaction is what a bank email says happened.
type EmailTransaction struct {
	Bank         string    `json:"bank"`
	Kind         string    `json:"kind"`
	Amount       float64   `json:"amount"`
	Currency     string    `json:"currency"`
	Counterparty string    `json:"counterparty,omitempty"`
	Source       string    `json:"source,omitempty"`
	Destination  string    `json:"destination,omitempty"`
	Method       string    `json:"method,omitempty"`
	Reference    string    `json:"reference,omitempty"`
	OccurredAt   time.Time `json:"occurred_at"`
}

// BankEmail is one email as forwarded to the server.
type BankEmail struct {
	From    string
	Subject string
	Date    time.Time
	// Text is the email body reduced to plain words, see flattenBody.
	Text string
}

var (
	// ErrNotTransaction marks emails from a bank that do not report a
	// transaction (new contact, card settings, failed payments).
	ErrNotTransaction = errors.New("email is not a transaction")
	// ErrUnknownSender marks emails from an address no parser handles.
	ErrUnknownSender = errors.New("sender is not a known bank")
)

var wib = time.FixedZone("WIB", 7*60*60)

type bankSender struct {
	bank  string
	parse func(BankEmail) (EmailTransaction, error)
}

// bankSenders maps a sender address to its bank and parser.
var bankSenders = map[string]bankSender{
	"bca@bca.co.id":               {"bca", parseBCA},
	"noreply@jago.com":            {"jago", parseJago},
	"receipts@blubybcadigital.id": {"blu", parseBlu},
}

// bankOfSender names the bank behind a sender address, or "" if it is not one.
func bankOfSender(from string) string {
	return bankSenders[senderAddress(from)].bank
}

// ParseBankEmail reads the transaction a bank email reports.
func ParseBankEmail(mail BankEmail) (EmailTransaction, error) {
	sender, ok := bankSenders[senderAddress(mail.From)]
	if !ok {
		return EmailTransaction{}, ErrUnknownSender
	}
	tx, err := sender.parse(mail)
	if err != nil {
		return EmailTransaction{}, err
	}
	if tx.Amount <= 0 {
		return EmailTransaction{}, errors.New("amount is missing")
	}
	tx.Currency = "IDR"
	tx.OccurredAt = tx.OccurredAt.UTC()
	return tx, nil
}

// senderAddress returns the bare lowercase address of a From header such as
// `BCA <bca@bca.co.id>`.
func senderAddress(from string) string {
	from = strings.TrimSpace(from)
	if start := strings.LastIndex(from, "<"); start >= 0 {
		if end := strings.Index(from[start:], ">"); end > 0 {
			from = from[start+1 : start+end]
		}
	}
	return strings.ToLower(strings.TrimSpace(from))
}

var (
	reHTMLBlocks = regexp.MustCompile(`(?is)<head.*?</head>|<style.*?</style>|<script.*?</script>|<!--.*?-->`)
	reHTMLTag    = regexp.MustCompile(`(?s)<[^>]*>`)
	reMDLink     = regexp.MustCompile(`\[([^\]]*)\]\([^)]*\)`)
	reSpaces     = regexp.MustCompile(`\s+`)
)

var invisibleChars = strings.NewReplacer(
	"\u00a0", " ", "\u200b", "", "\u200c", "", "\u200d", "", "\ufeff", "", "\u034f", "", "\u00ad", "",
	"|", " ",
)

// flattenBody reduces an email body to plain words separated by single
// spaces. Bank emails are tables inside tables, and how the cells are split
// differs between the HTML and the plain text version, so the parsers work on
// the words only and use the field labels as separators.
func flattenBody(htmlBody, textBody string) string {
	body := textBody
	if strings.TrimSpace(htmlBody) != "" {
		body = reHTMLBlocks.ReplaceAllString(htmlBody, " ")
		body = reHTMLTag.ReplaceAllString(body, " ")
		body = html.UnescapeString(body)
	}
	body = reMDLink.ReplaceAllString(body, "$1 ")
	body = invisibleChars.Replace(body)
	return strings.TrimSpace(reSpaces.ReplaceAllString(body, " "))
}

// parseAmount reads a money amount. Indonesian style uses dots for thousands
// and a comma for decimals ("12.000,00"); English style is the reverse
// ("18,000.00").
func parseAmount(raw string, indonesian bool) (float64, error) {
	raw = strings.TrimSpace(raw)
	if indonesian {
		raw = strings.ReplaceAll(raw, ".", "")
		raw = strings.ReplaceAll(raw, ",", ".")
	} else {
		raw = strings.ReplaceAll(raw, ",", "")
	}
	amount, err := strconv.ParseFloat(raw, 64)
	if err != nil {
		return 0, fmt.Errorf("unreadable amount %q", raw)
	}
	return amount, nil
}

var months = map[string]time.Month{
	"jan": 1, "januari": 1, "january": 1,
	"feb": 2, "februari": 2, "february": 2,
	"mar": 3, "maret": 3, "march": 3,
	"apr": 4, "april": 4,
	"mei": 5, "may": 5,
	"jun": 6, "juni": 6, "june": 6,
	"jul": 7, "juli": 7, "july": 7,
	"agu": 8, "agt": 8, "ags": 8, "aug": 8, "agustus": 8, "august": 8,
	"sep": 9, "sept": 9, "september": 9,
	"okt": 10, "oct": 10, "oktober": 10, "october": 10,
	"nov": 11, "november": 11, "nopember": 11,
	"des": 12, "dec": 12, "desember": 12, "december": 12,
}

var reDateTime = regexp.MustCompile(`(\d{1,2}) (\p{L}+) (\d{4}),? (\d{2}):(\d{2})(?::(\d{2}))?`)

// parseBankTime reads dates like "09 Nov 2026 18:23:47", "05 November 2026,
// 12:10" and "26 Okt 2026 12:11:08". Banks print them in Jakarta time.
func parseBankTime(raw string) (time.Time, error) {
	m := reDateTime.FindStringSubmatch(raw)
	if m == nil {
		return time.Time{}, fmt.Errorf("unreadable date %q", raw)
	}
	month, ok := months[strings.ToLower(m[2])]
	if !ok {
		return time.Time{}, fmt.Errorf("unknown month %q", m[2])
	}
	num := func(s string) int { n, _ := strconv.Atoi(s); return n }
	return time.Date(num(m[3]), month, num(m[1]), num(m[4]), num(m[5]), num(m[6]), 0, wib), nil
}

// ---- BCA (myBCA "Internet Transaction Journal") ----

var bcaLabels = []string{
	"Status", "Transaction Date", "Transaction Type", "Transfer Type", "Payment to",
	"Merchant Location", "Acquirer", "Merchant PAN", "Terminal ID", "Source of Fund",
	"Customer PAN", "Total Payment", "RRN", "Reference No.", "BCA Virtual Account No.",
	"Company/Product Name", "Pay Amount", "Description", "Beneficiary Account",
	"Beneficiary Name", "Beneficiary Bank", "Transfer Amount", "Amount", "Fee", "Remarks",
	"Name", "Note(s)",
}

var reBCALabel = func() *regexp.Regexp {
	quoted := make([]string, len(bcaLabels))
	for i, label := range bcaLabels {
		quoted[i] = regexp.QuoteMeta(label)
	}
	return regexp.MustCompile(`(?:^|\s)(` + strings.Join(quoted, "|") + `)\s*:`)
}()

// bcaFields splits the journal into its "Label : value" pairs.
func bcaFields(text string) map[string]string {
	fields := map[string]string{}
	matches := reBCALabel.FindAllStringSubmatchIndex(text, -1)
	for i, m := range matches {
		label := text[m[2]:m[3]]
		end := len(text)
		if i+1 < len(matches) {
			end = matches[i+1][0]
		}
		if _, seen := fields[label]; !seen {
			fields[label] = strings.TrimSpace(text[m[1]:end])
		}
	}
	return fields
}

var (
	reBCAAmount  = regexp.MustCompile(`IDR\s*([\d,]+(?:\.\d+)?)`)
	reBCAAccount = regexp.MustCompile(`[\dxX*]{6,}`)
)

func parseBCA(mail BankEmail) (EmailTransaction, error) {
	fields := bcaFields(mail.Text)
	status, ok := fields["Status"]
	if !ok {
		return EmailTransaction{}, ErrNotTransaction
	}
	if !strings.EqualFold(status, "Successful") {
		return EmailTransaction{}, ErrNotTransaction
	}

	tx := EmailTransaction{Bank: "bca", Reference: firstWord(fields["Reference No."])}

	occurred, err := parseBankTime(fields["Transaction Date"])
	if err != nil {
		return EmailTransaction{}, err
	}
	tx.OccurredAt = occurred

	for _, label := range []string{"Total Payment", "Transfer Amount", "Pay Amount", "Amount"} {
		if m := reBCAAmount.FindStringSubmatch(fields[label]); m != nil {
			if tx.Amount, err = parseAmount(m[1], false); err != nil {
				return EmailTransaction{}, err
			}
			break
		}
	}

	// "TAHAPAN XPRESI - 0123****45" and "0123xxxx45" are the same account.
	if account := reBCAAccount.FindString(fields["Source of Fund"]); account != "" {
		tx.Source = strings.NewReplacer("x", "*", "X", "*").Replace(account)
	}

	switch {
	case fields["Payment to"] != "":
		tx.Kind = KindPayment
		tx.Counterparty = fields["Payment to"]
		tx.Method = "QRIS"
	case fields["Company/Product Name"] != "":
		tx.Kind = KindTransfer
		tx.Counterparty = fields["Company/Product Name"]
		tx.Destination = tx.Counterparty
		tx.Method = "Virtual Account"
	default:
		tx.Kind = KindTransfer
		tx.Counterparty = firstNonEmpty(fields["Beneficiary Name"], fields["Name"])
		tx.Destination = firstNonEmpty(fields["Beneficiary Account"], tx.Counterparty)
		tx.Method = firstNonEmpty(fields["Transfer Type"], fields["Transaction Type"])
	}
	return tx, nil
}

// ---- Jago ----

var (
	reJagoSummary = regexp.MustCompile(`Ringkasan transaksi\s+Dari:?\s+(.+?)\s+Ke:?\s+(.+?)\s+Jumlah:?\s+Rp\s?([\d.,]+)\s+Tanggal [Tt]ransaksi:?\s+(\d{1,2} \p{L}+ \d{4},? \d{2}:\d{2})`)
	reJagoPaidTo  = regexp.MustCompile(`membayar ke (.+)$`)
	reJagoMoved   = regexp.MustCompile(`(?:memindahkan uang sebesar Rp\s?([\d.,]+)|Rp\s?([\d.,]+) telah dipindahkan) dari Kantong (.+?) ke Kantong (.+?)\.(?:\s|$)`)
	reJagoTaken   = regexp.MustCompile(`penarikan sebesar Rp\s?([\d.,]+) dari Kantong (.+?)\.(?:\s|$)`)
	reJagoGot     = regexp.MustCompile(`menerima Rp\s?([\d.,]+) dari (?:akun )?(.+?)\.(?:\s|$)`)
	reLongDigits  = regexp.MustCompile(`\s*\d{15,}$`)
	reAccountNo   = regexp.MustCompile(`\d{9,}`)
)

func parseJago(mail BankEmail) (EmailTransaction, error) {
	subject := cleanSubject(mail.Subject)
	tx := EmailTransaction{Bank: "jago", OccurredAt: mail.Date}

	amountOf := func(raw string) (float64, error) {
		return parseAmount(strings.TrimRight(raw, ".,"), true)
	}

	// Moves between pockets and incoming money are written as a sentence.
	if m := reJagoMoved.FindStringSubmatch(mail.Text); m != nil {
		amount, err := amountOf(firstNonEmpty(m[1], m[2]))
		if err != nil {
			return EmailTransaction{}, err
		}
		tx.Kind, tx.Amount, tx.Source, tx.Destination = KindPocketMove, amount, m[3], m[4]
		return tx, nil
	}
	if m := reJagoTaken.FindStringSubmatch(mail.Text); m != nil {
		amount, err := amountOf(m[1])
		if err != nil {
			return EmailTransaction{}, err
		}
		tx.Kind, tx.Amount, tx.Source = KindPocketMove, amount, m[2]
		return tx, nil
	}
	if m := reJagoGot.FindStringSubmatch(mail.Text); m != nil {
		amount, err := amountOf(m[1])
		if err != nil {
			return EmailTransaction{}, err
		}
		tx.Kind, tx.Amount, tx.Counterparty = KindIncoming, amount, m[2]
		return tx, nil
	}

	m := reJagoSummary.FindStringSubmatch(mail.Text)
	if m == nil {
		return EmailTransaction{}, ErrNotTransaction
	}
	amount, err := amountOf(m[3])
	if err != nil {
		return EmailTransaction{}, err
	}
	occurred, err := parseBankTime(m[4])
	if err != nil {
		return EmailTransaction{}, err
	}
	tx.Amount, tx.OccurredAt = amount, occurred
	tx.Source = firstNonEmpty(reAccountNo.FindString(m[1]), m[1])
	target := strings.TrimSpace(reLongDigits.ReplaceAllString(m[2], ""))

	switch {
	case strings.Contains(subject, "top up"):
		tx.Kind, tx.Destination, tx.Counterparty = KindTopUp, target, target
	case strings.Contains(subject, "membayar ke"):
		tx.Kind, tx.Counterparty = KindPayment, target
		if paid := reJagoPaidTo.FindStringSubmatch(subject); paid != nil && target == "" {
			tx.Counterparty = paid[1]
		}
		if strings.Contains(mail.Text, "Nama Acquirer") {
			tx.Method = "QRIS"
		}
	default:
		tx.Kind, tx.Destination, tx.Counterparty = KindTransfer, target, target
	}
	return tx, nil
}

// ---- blu by BCA Digital ----

var (
	reBluTotal   = regexp.MustCompile(`\bTotal(?: Bayar)?\s+-?Rp\s?([\d.]+(?:,\d+)?)`)
	reBluNominal = regexp.MustCompile(`\bNominal(?: Transfer)?\s+-?Rp\s?([\d.]+(?:,\d+)?)`)
	reBluTime    = regexp.MustCompile(`Tgl & Jam Transaksi\s+(\d{1,2} \p{L}+ \d{4},? \d{2}:\d{2}(?::\d{2})?)`)
	reBluType    = regexp.MustCompile(`Tipe Transaksi\s+(.+?)\s+(?:No\. Ref|Cheers|Catatan|Perlu bantuan)`)
	reBluRef     = regexp.MustCompile(`No\. Ref blu\s+([0-9A-Za-z ]+?)\s+(?:Cheers|Catatan|Perlu bantuan)`)
	reBluGreet   = regexp.MustCompile(`Hai ([^,]+),`)
	reBluParty   = regexp.MustCompile(`bluAccount\s+(.+?)\s+(?:Nominal Tagihan|Biaya Admin|Order ID|Tgl & Jam Transaksi|Total|Nominal|Catatan)\b`)
	reBluSender  = regexp.MustCompile(`Rp\s?[\d.]+(?:,\d+)?\s+(.+?)\s+bluAccount`)
	reEWallet    = regexp.MustCompile(`(?i)shopeepay|gopay|ovo|dana|linkaja`)
)

func parseBlu(mail BankEmail) (EmailTransaction, error) {
	text := mail.Text
	tx := EmailTransaction{Bank: "blu"}

	timeMatch := reBluTime.FindStringSubmatch(text)
	if timeMatch == nil {
		return EmailTransaction{}, ErrNotTransaction
	}
	occurred, err := parseBankTime(timeMatch[1])
	if err != nil {
		return EmailTransaction{}, err
	}
	tx.OccurredAt = occurred

	// "Total" is what left the account (fees and rewards included). Emails
	// without a total only carry the nominal.
	amountRaw := ""
	if totals := reBluTotal.FindAllStringSubmatch(text, -1); len(totals) > 0 {
		amountRaw = totals[len(totals)-1][1]
	} else if m := reBluNominal.FindStringSubmatch(text); m != nil {
		amountRaw = m[1]
	}
	if amountRaw == "" {
		return EmailTransaction{}, errors.New("amount is missing")
	}
	if tx.Amount, err = parseAmount(amountRaw, true); err != nil {
		return EmailTransaction{}, err
	}

	if m := reBluType.FindStringSubmatch(text); m != nil {
		tx.Method = m[1]
	}
	if m := reBluRef.FindStringSubmatch(text); m != nil {
		tx.Reference = strings.ReplaceAll(m[1], " ", "")
	}

	if strings.Contains(cleanSubject(mail.Subject), "transaksi masuk") {
		tx.Kind, tx.Destination = KindIncoming, "blu"
		if m := reBluSender.FindStringSubmatch(text); m != nil {
			sender := m[1]
			// The cell holds the sender followed by the owner's own name. The
			// greeting gives the owner's first name, so cut there.
			if greet := reBluGreet.FindStringSubmatch(text); greet != nil {
				if cut := strings.LastIndex(sender, greet[1]); cut > 0 {
					sender = sender[:cut]
				}
			}
			tx.Counterparty = strings.TrimSpace(sender)
		}
		return tx, nil
	}

	tx.Source = "blu"
	if m := reBluParty.FindStringSubmatch(text); m != nil {
		tx.Counterparty = m[1]
	}
	switch {
	case strings.EqualFold(tx.Method, "QRIS"):
		tx.Kind = KindPayment
	case reEWallet.MatchString(tx.Counterparty):
		tx.Kind, tx.Destination = KindTopUp, tx.Counterparty
	case tx.Method != "":
		tx.Kind, tx.Destination = KindTransfer, tx.Counterparty
	default:
		tx.Kind = KindPayment
	}
	return tx, nil
}

// ---- helpers ----

// cleanSubject lowercases a subject and drops emoji and invisible characters.
func cleanSubject(subject string) string {
	subject = invisibleChars.Replace(subject)
	var b strings.Builder
	for _, r := range subject {
		if r < 0x2190 { // keeps letters and punctuation, drops symbols and emoji
			b.WriteRune(r)
		}
	}
	return strings.ToLower(strings.TrimSpace(strings.TrimRight(strings.TrimSpace(b.String()), ".")))
}

func firstWord(s string) string {
	if fields := strings.Fields(s); len(fields) > 0 {
		return fields[0]
	}
	return ""
}

func firstNonEmpty(values ...string) string {
	for _, v := range values {
		if strings.TrimSpace(v) != "" {
			return strings.TrimSpace(v)
		}
	}
	return ""
}
