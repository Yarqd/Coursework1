create table review_reminders (
    chat_id bigint primary key,
    due_card_signature text not null,
    reminded_at timestamp with time zone,
    acknowledged_at timestamp with time zone,
    updated_at timestamp with time zone not null
);
