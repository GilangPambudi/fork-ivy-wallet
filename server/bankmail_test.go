package main

import (
	"errors"
	"strings"
	"testing"
	"time"
)

// The fixtures below follow the layout of real bank emails. Names, account
// numbers and references are made up.

const bcaQRISText = `| | Hello BUDI SANTOSO,
You just made a transaction through myBCA.
Here are the details of your transaction :

| Status | : | Successful |
| Transaction Date | : | 09 Nov 2026 18:23:47 |
| Transaction Type | : | QRIS Payment |
| Payment to | : | Toko Sumber Rejeki |
| Merchant Location | : | MALANG, 65141, ID |
| Acquirer | : | BCA |
| Merchant PAN | : | 9360001400000000001 |
| Terminal ID | : | A01 |
| Source of Fund | : | TAHAPAN XPRESI - 0123****45 |
| Customer PAN | : | 9360001410000000002 |
| Total Payment | : | IDR 18,000.00 |
| RRN | : | 412932292 |
| Reference No. | : | 9527120261109182345119QRS0000000001 |

Please save this email as your transaction reference.
If you do not recognize this transaction, immediately contact Halo BCA at 1500888.`

const bcaVirtualAccountHTML = `<html><head><title>Journal</title><style>td { color: #333; }</style></head><body>
<table><tr><td>Hello BUDI SANTOSO,<br>You just made a transaction through myBCA.<br>Here are the details of your transaction :</td></tr></table>
<table>
<tr><td>Status</td><td>:</td><td><b>Successful</b></td></tr>
<tr><td>Transaction&nbsp;Date</td><td>:</td><td>10 Nov 2026 18:56:43</td></tr>
<tr><td>Transfer Type</td><td>:</td><td>Transfer to BCA Virtual Account</td></tr>
<tr><td>Source of Fund</td><td>:</td><td>0123xxxx45</td></tr>
<tr><td>BCA Virtual Account No.</td><td>:</td><td>122080000000001</td></tr>
<tr><td>Name</td><td>:</td><td>BUDXXX SANXXXX</td></tr>
<tr><td>Company/Product Name</td><td>:</td><td>PT AIRPAY INTERNATIONAL INDONE / SHOPEEPAY</td></tr>
<tr><td>Pay Amount</td><td>:</td><td>IDR 100,000.00</td></tr>
<tr><td>Total Payment</td><td>:</td><td>IDR 100,000.00</td></tr>
<tr><td>Description</td><td>:</td><td>BUDXXX SANXXXX</td></tr>
<tr><td>Reference No.</td><td>:</td><td>9527120261110185643073TVA0000000002</td></tr>
</table>
<p>Note(s):<br>Fees Include VAT (if any)</p><!-- tracking -->
<p>Please save this email as your transaction reference.</p></body></html>`

const jagoPaymentText = ` Jago Mail
[](https://r.mail.example/tr/cl/abc)

Assalamu'alaikum BUDI SANTOSO,

Terima kasih sudah bertransaksi dengan Jago! Kamu baru saja mengirimkan uang, berikut rinciannya:

| Ringkasan transaksi | | |
| Dari | 500500500500 |
| Ke | WARUNG BU TINI, LWK 9360091400000000003 |
| Jumlah | Rp 14.000 |
| Tanggal Transaksi | 05 November 2026, 12:10 WIB |
| Status Transaksi | Berhasil |
| Nama Acquirer | GoPay |
| Lokasi Merchant | MALANG |
| ID Terminal | A02 |
| PAN Nasabah | 9360054210000000004 |
| Jumlah Tip | Rp 0 |

Dana telah terkirim. Cek detil riwayat transaksi di aplikasi Jago melalui Rincian Kantong.
Dari nabung, transaksi, hingga investasi, lakukan dengan caramu sendiri.`

const jagoTopUpText = `Assalamu'alaikum BUDI SANTOSO,

Terima kasih sudah bertransaksi dengan Jago! Kamu baru saja melakukan top up, berikut rinciannya:

| Ringkasan transaksi | | |
| Dari: | 500500500500 |
| Ke: | 081200000001 |
| Jumlah: | Rp98.000 |
| Tanggal transaksi: | 09 November 2026 08:34 WIB |

Jago mau bikin hidup kamu jadi bebas ribet!`

const jagoTransferText = `Assalamu'alaikum BUDI SANTOSO,

Terima kasih sudah bertransaksi dengan Jago! Kamu baru saja melakukan transfer uang, berikut rinciannya:

| Ringkasan transaksi | | |
| Dari | Utama • 500500500500 |
| Ke | BUDI SANTOSO BCA Digital • 009400000005 |
| Jumlah | Rp650.000 |
| Tanggal transaksi | 04 November 2026 20:26 WIB |

Cek detil riwayat transaksi di aplikasi Jago melalui Rincian Kantong.`

const bluQRISText = `| Hai Budi, Terima kasih sudah menggunakan blu untuk transaksimu. Ini detail transaksi yang telah kamu lakukan: |
| Total |
| Rp12.000,00 |
| Budi Santoso bluAccount | | WARUNG BU TINI, LWK MALANG |
| Nominal Tagihan |
| Rp12.000,00 |
| Tgl & Jam Transaksi |
| 26 Okt 2026 12:11:08 WIB |
| Tipe Transaksi |
| QRIS |
| No. Ref blu |
| 1tk8 mn48 5118 |
| Cheers, |
| #teamblu |`

const bluTransferHTML = `<html><body><table>
<tr><td>Hai Budi, Terima kasih sudah menggunakan blu untuk transaksimu. Ini detail transaksi yang telah kamu lakukan:</td></tr>
<tr><td>Nominal</td></tr><tr><td>Rp150.000,00</td></tr>
<tr><td><span>Budi Santoso</span><br><span>bluAccount</span></td><td></td><td><span>BUDI SANTOSO...</span><br><span>BANK JAGO</span><br><span>5005 0050 0500</span></td></tr>
<tr><td>Biaya Admin</td><td>Rp2.500,00</td></tr>
<tr><td>bluRewards</td><td>-Rp2.500,00</td></tr>
<tr><td>Total</td><td>Rp150.000,00</td></tr>
<tr><td>Tgl &amp; Jam Transaksi</td><td>30 Okt 2026 18:27:59 WIB</td></tr>
<tr><td>Tipe Transaksi</td><td>BI-FAST</td></tr>
<tr><td>No. Ref blu</td><td>1793 3596 7923 4262</td></tr>
<tr><td>Cheers,</td></tr></table></body></html>`

const bluTopUpText = `| Hai Budi, Terima kasih sudah menggunakan blu untuk transaksimu. Ini detail transaksi yang telah kamu lakukan: |
| Total Bayar |
| Rp15.000,00 |
| Budi Santoso bluAccount | | budisan ShopeePay - 0851 0000 0001 |
| Tgl & Jam Transaksi |
| 19 Okt 2026 07:08:20 WIB |
| No. Ref blu |
| 1792 3685 0000 0001 |
| Cheers, |`

const bluIncomingText = `| Hai Budi, Ada transaksi masuk ke rekening bluAccount kamu. Ini detailnya: |
| Nominal Transfer |
| Rp650.000,00 |
| BUDI SANTOSO BANK JAGO UUS | | Budi Santoso bluAccount 0094 0000 0005 |
| Tgl & Jam Transaksi |
| 04 Nov 2026 20:26:41 WIB |
| Tipe Transaksi |
| BI-FAST |
| Cheers, |
| #teamblu |`

func jakarta(year int, month time.Month, day, hour, minute, second int) time.Time {
	return time.Date(year, month, day, hour, minute, second, 0, wib).UTC()
}

var emailDate = time.Date(2026, 11, 8, 7, 37, 20, 0, time.UTC)

func TestParseBankEmail(t *testing.T) {
	cases := []struct {
		name    string
		from    string
		subject string
		html    string
		text    string
		want    EmailTransaction
	}{
		{
			name: "bca qris payment", from: "BCA <bca@bca.co.id>", subject: "Internet Transaction Journal",
			text: bcaQRISText,
			want: EmailTransaction{
				Bank: "bca", Kind: KindPayment, Amount: 18000, Counterparty: "Toko Sumber Rejeki",
				Source: "0123****45", Method: "QRIS", Reference: "9527120261109182345119QRS0000000001",
				OccurredAt: jakarta(2026, 11, 9, 18, 23, 47),
			},
		},
		{
			name: "bca virtual account from html", from: "bca@bca.co.id", subject: "Internet Transaction Journal",
			html: bcaVirtualAccountHTML,
			want: EmailTransaction{
				Bank: "bca", Kind: KindTransfer, Amount: 100000,
				Counterparty: "PT AIRPAY INTERNATIONAL INDONE / SHOPEEPAY",
				Destination:  "PT AIRPAY INTERNATIONAL INDONE / SHOPEEPAY",
				Source:       "0123****45", Method: "Virtual Account",
				Reference:  "9527120261110185643073TVA0000000002",
				OccurredAt: jakarta(2026, 11, 10, 18, 56, 43),
			},
		},
		{
			name: "jago qris payment", from: "Jago <noreply@jago.com>",
			subject: "Kamu telah membayar ke WARUNG BU TINI, LWK💸", text: jagoPaymentText,
			want: EmailTransaction{
				Bank: "jago", Kind: KindPayment, Amount: 14000, Counterparty: "WARUNG BU TINI, LWK",
				Source: "500500500500", Method: "QRIS", OccurredAt: jakarta(2026, 11, 5, 12, 10, 0),
			},
		},
		{
			name: "jago e-wallet top up", from: "noreply@jago.com",
			subject: "Kamu telah melakukan top up e-Wallet💸", text: jagoTopUpText,
			want: EmailTransaction{
				Bank: "jago", Kind: KindTopUp, Amount: 98000, Counterparty: "081200000001",
				Destination: "081200000001", Source: "500500500500", OccurredAt: jakarta(2026, 11, 9, 8, 34, 0),
			},
		},
		{
			name: "jago transfer", from: "noreply@jago.com",
			subject: "Kamu telah melakukan transfer💸", text: jagoTransferText,
			want: EmailTransaction{
				Bank: "jago", Kind: KindTransfer, Amount: 650000,
				Counterparty: "BUDI SANTOSO BCA Digital • 009400000005",
				Destination:  "BUDI SANTOSO BCA Digital • 009400000005",
				Source:       "500500500500", OccurredAt: jakarta(2026, 11, 4, 20, 26, 0),
			},
		},
		{
			name: "jago move between pockets", from: "noreply@jago.com",
			subject: "Kamu telah memindahkan uang ke Kantong lain",
			text:    "Assalamu'alaikum BUDI SANTOSO, Kamu telah memindahkan uang sebesar Rp1.750.000 dari Kantong Tabungan Liburan ke Kantong Code. Kamu bisa melihat riwayat transaksi di Rincian Kantong melalui aplikasi Jago.",
			want: EmailTransaction{
				Bank: "jago", Kind: KindPocketMove, Amount: 1750000,
				Source: "Tabungan Liburan", Destination: "Code", OccurredAt: emailDate,
			},
		},
		{
			name: "jago move between pockets, other wording", from: "noreply@jago.com",
			subject: "Pemindahan dana antar Kantong",
			text:    "Halo BUDI SANTOSO, Rp65.000 telah dipindahkan dari Kantong Kantong Utama ke Kantong Tabungan Liburan. Kamu dapat melihat detail transaksi lengkap kapan saja.",
			want: EmailTransaction{
				Bank: "jago", Kind: KindPocketMove, Amount: 65000,
				Source: "Kantong Utama", Destination: "Tabungan Liburan", OccurredAt: emailDate,
			},
		},
		{
			name: "jago withdrawal from a pocket", from: "noreply@jago.com",
			subject: "Kamu memindahkan uang dari salah satu Kantong kamu",
			text:    "Assalamu'alaikum BUDI SANTOSO, Kamu baru saja melakukan penarikan sebesar Rp65.000 dari Kantong Langganan. Kamu bisa melihat riwayat transaksi di Rincian Kantong.",
			want: EmailTransaction{
				Bank: "jago", Kind: KindPocketMove, Amount: 65000, Source: "Langganan", OccurredAt: emailDate,
			},
		},
		{
			name: "jago incoming", from: "noreply@jago.com",
			subject: "Kamu telah menerima uang di Jago dari GoPay",
			text:    "Halo BUDI SANTOSO, Kamu telah menerima Rp1.750.000 dari akun Gopay. Kamu bisa melihat riwayat transaksi di Rincian Kantong melalui aplikasi Jago.",
			want: EmailTransaction{
				Bank: "jago", Kind: KindIncoming, Amount: 1750000, Counterparty: "Gopay", OccurredAt: emailDate,
			},
		},
		{
			name: "blu qris payment", from: "blu <receipts@blubybcadigital.id>",
			subject: "Transaksimu Pakai blu Berhasil", text: bluQRISText,
			want: EmailTransaction{
				Bank: "blu", Kind: KindPayment, Amount: 12000, Counterparty: "WARUNG BU TINI, LWK MALANG",
				Source: "blu", Method: "QRIS", Reference: "1tk8mn485118", OccurredAt: jakarta(2026, 10, 26, 12, 11, 8),
			},
		},
		{
			name: "blu transfer from html", from: "receipts@blubybcadigital.id",
			subject: "Transaksimu Pakai blu Berhasil", html: bluTransferHTML,
			want: EmailTransaction{
				Bank: "blu", Kind: KindTransfer, Amount: 150000,
				Counterparty: "BUDI SANTOSO... BANK JAGO 5005 0050 0500",
				Destination:  "BUDI SANTOSO... BANK JAGO 5005 0050 0500",
				Source:       "blu", Method: "BI-FAST", Reference: "1793359679234262",
				OccurredAt: jakarta(2026, 10, 30, 18, 27, 59),
			},
		},
		{
			name: "blu e-wallet top up", from: "receipts@blubybcadigital.id",
			subject: "Transaksimu Pakai blu Berhasil", text: bluTopUpText,
			want: EmailTransaction{
				Bank: "blu", Kind: KindTopUp, Amount: 15000,
				Counterparty: "budisan ShopeePay - 0851 0000 0001", Destination: "budisan ShopeePay - 0851 0000 0001",
				Source: "blu", Reference: "1792368500000001", OccurredAt: jakarta(2026, 10, 19, 7, 8, 20),
			},
		},
		{
			name: "blu incoming", from: "receipts@blubybcadigital.id",
			subject: "Info Transaksi Masuk ke blu Kamu 💸", text: bluIncomingText,
			want: EmailTransaction{
				Bank: "blu", Kind: KindIncoming, Amount: 650000, Counterparty: "BUDI SANTOSO BANK JAGO UUS",
				Destination: "blu", Method: "BI-FAST", OccurredAt: jakarta(2026, 11, 4, 20, 26, 41),
			},
		},
	}

	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			got, err := ParseBankEmail(BankEmail{
				From: c.from, Subject: c.subject, Date: emailDate, Text: flattenBody(c.html, c.text),
			})
			if err != nil {
				t.Fatalf("ParseBankEmail: %v", err)
			}
			c.want.Currency = "IDR"
			if !got.OccurredAt.Equal(c.want.OccurredAt) {
				t.Errorf("OccurredAt = %v, want %v", got.OccurredAt, c.want.OccurredAt)
			}
			got.OccurredAt, c.want.OccurredAt = time.Time{}, time.Time{}
			if got != c.want {
				t.Errorf("got  %+v\nwant %+v", got, c.want)
			}
		})
	}
}

func TestParseBankEmailSkipsWhatIsNotATransaction(t *testing.T) {
	cases := []struct {
		name    string
		from    string
		subject string
		text    string
		want    error
	}{
		{
			name: "newsletter from another address", from: "announcement@info.blubybcadigital.id",
			subject: "Promo", text: "Nikmati suku bunga", want: ErrUnknownSender,
		},
		{
			name: "jago new contact", from: "noreply@jago.com", subject: "Kontak baru ditambahkan ke daftar kontak",
			text: "Kamu telah menambahkan SITI AMINAH ke daftar kontak. Kamu bisa dengan mudah mengatur daftar.",
			want: ErrNotTransaction,
		},
		{
			name: "jago card update", from: "noreply@jago.com", subject: "Kamu telah memperbarui rincian salah satu kartu debitmu",
			text: "Rincian kartu Kartu Syariah telah berhasil diperbarui.", want: ErrNotTransaction,
		},
		{
			name: "bca failed transaction", from: "bca@bca.co.id", subject: "Internet Transaction Journal",
			text: strings.Replace(bcaQRISText, "Successful", "Failed", 1), want: ErrNotTransaction,
		},
		{
			name: "blu without transaction details", from: "receipts@blubybcadigital.id",
			subject: "Info", text: "Hai Budi, ini bukan transaksi.", want: ErrNotTransaction,
		},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			_, err := ParseBankEmail(BankEmail{From: c.from, Subject: c.subject, Date: emailDate, Text: flattenBody("", c.text)})
			if !errors.Is(err, c.want) {
				t.Fatalf("error = %v, want %v", err, c.want)
			}
		})
	}
}

func TestParseBankEmailReportsUnreadableTransactions(t *testing.T) {
	// A bank changing its layout must surface as an error, not as a silent skip
	// or a wrong amount.
	broken := strings.Replace(bcaQRISText, "IDR 18,000.00", "see attachment", 1)
	_, err := ParseBankEmail(BankEmail{From: "bca@bca.co.id", Date: emailDate, Text: flattenBody("", broken)})
	if err == nil || errors.Is(err, ErrNotTransaction) || errors.Is(err, ErrUnknownSender) {
		t.Fatalf("error = %v, want a parse error", err)
	}
}

func TestFlattenBody(t *testing.T) {
	got := flattenBody(`<html><head><style>p{}</style></head><body><p>Total&nbsp;Bayar</p><td>Rp 1.000</td><!-- x --><script>alert(1)</script></body></html>`, "ignored when html is present")
	if want := "Total Bayar Rp 1.000"; got != want {
		t.Errorf("html: got %q, want %q", got, want)
	}
	got = flattenBody("", "| Dari | 123 |\n| Ke | [Toko](https://example.com) |")
	if want := "Dari 123 Ke Toko"; got != want {
		t.Errorf("text: got %q, want %q", got, want)
	}
}

func TestParseAmount(t *testing.T) {
	cases := []struct {
		raw        string
		indonesian bool
		want       float64
	}{
		{"18,000.00", false, 18000},
		{"1,750,000.50", false, 1750000.5},
		{"12.000,00", true, 12000},
		{"1.750.000", true, 1750000},
		{"14.000", true, 14000},
		{"116.210,00", true, 116210},
	}
	for _, c := range cases {
		got, err := parseAmount(c.raw, c.indonesian)
		if err != nil || got != c.want {
			t.Errorf("parseAmount(%q, %v) = %v, %v; want %v", c.raw, c.indonesian, got, err, c.want)
		}
	}
}
