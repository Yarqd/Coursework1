create table app_users (
    id bigserial primary key,
    chat_id bigint not null unique,
    username varchar(255),
    first_name varchar(255),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table media_assets (
    id bigserial primary key,
    storage_key varchar(512) not null unique,
    telegram_file_id text,
    telegram_file_unique_id varchar(255),
    mime_type varchar(255) not null,
    file_size bigint not null,
    width integer,
    height integer,
    created_at timestamp with time zone not null
);

create table decks (
    id varchar(80) primary key,
    owner_chat_id bigint,
    title varchar(255) not null,
    source varchar(32) not null,
    ready_source_id varchar(80),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index idx_decks_owner_chat_id on decks(owner_chat_id);

create table cards (
    id bigserial primary key,
    deck_id varchar(80) not null references decks(id) on delete cascade,
    order_index integer not null,
    question_type varchar(32) not null,
    question_text text,
    question_media_id bigint references media_assets(id),
    answer_type varchar(32) not null,
    answer_text text,
    answer_media_id bigint references media_assets(id),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index idx_cards_deck_id_order on cards(deck_id, order_index);

create table fsrs_card_states (
    id bigserial primary key,
    chat_id bigint not null,
    card_id bigint not null references cards(id) on delete cascade,
    difficulty double precision not null,
    stability double precision not null,
    repetitions integer not null,
    lapses integer not null,
    last_reviewed_at timestamp with time zone,
    due_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uq_fsrs_chat_card unique (chat_id, card_id)
);

create table review_logs (
    id bigserial primary key,
    chat_id bigint not null,
    card_id bigint not null references cards(id) on delete cascade,
    rating varchar(32) not null,
    reviewed_at timestamp with time zone not null,
    scheduled_days integer not null,
    difficulty double precision not null,
    stability double precision not null
);

create index idx_review_logs_chat_id on review_logs(chat_id);
