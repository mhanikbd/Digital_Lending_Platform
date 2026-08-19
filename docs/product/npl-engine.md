# DPD, classification and collections

> **Status: design intent. Implemented in Milestones 29 to 31.**

## 1. Two separate concepts

Operational collection alerts and regulatory classification are **not the same
thing** and must not share thresholds. A bank may want a collector to call at 5
days past due while the regulatory classification changes at 30, 60 or 90. Tying
them together produces either regulatory misreporting or a useless collection
queue.

They are configured separately:

| Table | Purpose |
| ----- | ------- |
| `t_dpd_rule` | How days past due are counted and bucketed |
| `t_loan_classification_rule` | Regulatory classification thresholds |
| `t_npl_alert_rule` | Operational alerting |

No threshold is hard-coded. A change in regulatory guidance is a configuration
change, and each version is dated so historical classification remains
reproducible.

## 2. Daily process

```
Calculate due
  → Calculate DPD
  → Update loan
  → Generate alerts
  → Update classification
  → Create collection queue entries
```

Scheduled, idempotent, and safe to re-run for a given business date: a failed or
partially completed run must be repeatable without double-counting.

## 3. Collections

Collection queue, collector assignment, DPD buckets, customer contact, call log,
visit, promise to pay, follow-up, SMS, collection note, recovery, settlement and
escalation.

## 4. Notifications

Templates in English and Bangla, delivered by push, SMS, email and in-app.

Events: application submitted, application returned, query raised, query
answered, loan approved, loan rejected, sanction letter, disbursement, EMI due,
EMI due tomorrow, EMI overdue, NPL alert, payment success, payment failure, loan
closed.

Tables: `t_notification_template`, `t_notification_event`,
`t_notification_queue`, `t_notification_log`.
