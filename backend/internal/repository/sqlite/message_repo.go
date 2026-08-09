package sqlite

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/repository"
)

type sqliteMessageRepo struct {
	db *sql.DB
}

func NewMessageRepo(db *sql.DB) repository.MessageRepository {
	return &sqliteMessageRepo{db: db}
}

func (r *sqliteMessageRepo) ListPredefinedMessages(ctx context.Context, includeInactive bool) ([]*domain.PredefinedMessage, error) {
	query := `SELECT id, code, COALESCE(protocol_code, 0), message_en, message_fil, message_bikol, message_type, emergency_type_id, is_active, created_at, updated_at FROM predefined_messages`
	if !includeInactive {
		query += ` WHERE is_active = 1`
	}
	query += ` ORDER BY id ASC`

	rows, err := r.db.QueryContext(ctx, query)
	if err != nil {
		return nil, fmt.Errorf("failed to list predefined messages: %w", err)
	}
	defer rows.Close()

	var messages []*domain.PredefinedMessage
	for rows.Next() {
		message, err := scanPredefinedMessage(rows)
		if err != nil {
			return nil, err
		}
		messages = append(messages, message)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}

	return messages, nil
}

func (r *sqliteMessageRepo) CreatePredefinedMessage(ctx context.Context, message *domain.PredefinedMessage) (*domain.PredefinedMessage, error) {
	nowStr := time.Now().UTC().Format(time.RFC3339Nano)
	res, err := r.db.ExecContext(ctx, `
		INSERT INTO predefined_messages
			(code, protocol_code, message_en, message_fil, message_bikol, message_type, emergency_type_id, is_active, created_at, updated_at)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
	`, message.Code, message.ProtocolCode, message.MessageEN, message.MessageFil, message.MessageBikol, message.MessageType, message.EmergencyTypeID, boolToInt(message.IsActive), nowStr, nowStr)
	if err != nil {
		return nil, fmt.Errorf("failed to create predefined message: %w", err)
	}

	id, err := res.LastInsertId()
	if err != nil {
		return nil, err
	}
	return r.getPredefinedMessageByID(ctx, id)
}

func (r *sqliteMessageRepo) UpdatePredefinedMessage(ctx context.Context, message *domain.PredefinedMessage) (*domain.PredefinedMessage, error) {
	nowStr := time.Now().UTC().Format(time.RFC3339Nano)
	res, err := r.db.ExecContext(ctx, `
		UPDATE predefined_messages
		SET code = ?, protocol_code = ?, message_en = ?, message_fil = ?, message_bikol = ?, message_type = ?,
			emergency_type_id = ?, is_active = ?, updated_at = ?
		WHERE id = ?
	`, message.Code, message.ProtocolCode, message.MessageEN, message.MessageFil, message.MessageBikol, message.MessageType, message.EmergencyTypeID, boolToInt(message.IsActive), nowStr, message.ID)
	if err != nil {
		return nil, fmt.Errorf("failed to update predefined message: %w", err)
	}

	affected, _ := res.RowsAffected()
	if affected == 0 {
		return nil, domain.ErrNotFound
	}
	return r.getPredefinedMessageByID(ctx, message.ID)
}

func (r *sqliteMessageRepo) DeletePredefinedMessage(ctx context.Context, id int64) error {
	res, err := r.db.ExecContext(ctx, `DELETE FROM predefined_messages WHERE id = ?`, id)
	if err != nil {
		return fmt.Errorf("failed to delete predefined message: %w", err)
	}
	affected, _ := res.RowsAffected()
	if affected == 0 {
		return domain.ErrNotFound
	}
	return nil
}

func (r *sqliteMessageRepo) ListIncidentMessages(ctx context.Context, incidentID int64) ([]*domain.IncidentMessage, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT
			im.id, im.incident_id, im.sender_user_id, im.receiver_user_id, im.predefined_message_id,
			im.custom_message, im.channel, im.sent_at, im.received_at,
			pm.code, pm.message_en, sender.username, receiver.username
		FROM incident_messages im
		LEFT JOIN predefined_messages pm ON pm.id = im.predefined_message_id
		LEFT JOIN users sender ON sender.id = im.sender_user_id
		LEFT JOIN users receiver ON receiver.id = im.receiver_user_id
		WHERE im.incident_id = ?
		ORDER BY im.sent_at ASC, im.id ASC
	`, incidentID)
	if err != nil {
		return nil, fmt.Errorf("failed to list incident messages: %w", err)
	}
	defer rows.Close()

	var messages []*domain.IncidentMessage
	for rows.Next() {
		message, err := scanIncidentMessage(rows)
		if err != nil {
			return nil, err
		}
		messages = append(messages, message)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	return messages, nil
}

func (r *sqliteMessageRepo) CreateIncidentMessage(ctx context.Context, message *domain.IncidentMessage) (*domain.IncidentMessage, error) {
	nowStr := time.Now().UTC().Format(time.RFC3339Nano)
	res, err := r.db.ExecContext(ctx, `
		INSERT INTO incident_messages
			(incident_id, sender_user_id, receiver_user_id, predefined_message_id, custom_message, channel, sent_at, received_at)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?)
	`, message.IncidentID, message.SenderUserID, message.ReceiverUserID, message.PredefinedMessageID, nullableString(message.CustomMessage), message.Channel, nowStr, nullableTimeString(message.ReceivedAt))
	if err != nil {
		return nil, fmt.Errorf("failed to create incident message: %w", err)
	}

	id, err := res.LastInsertId()
	if err != nil {
		return nil, err
	}
	return r.getIncidentMessageByID(ctx, id)
}

func (r *sqliteMessageRepo) getIncidentMessageByID(ctx context.Context, id int64) (*domain.IncidentMessage, error) {
	row := r.db.QueryRowContext(ctx, `
		SELECT
			im.id, im.incident_id, im.sender_user_id, im.receiver_user_id, im.predefined_message_id,
			im.custom_message, im.channel, im.sent_at, im.received_at,
			pm.code, pm.message_en, sender.username, receiver.username
		FROM incident_messages im
		LEFT JOIN predefined_messages pm ON pm.id = im.predefined_message_id
		LEFT JOIN users sender ON sender.id = im.sender_user_id
		LEFT JOIN users receiver ON receiver.id = im.receiver_user_id
		WHERE im.id = ?
	`, id)
	message, err := scanIncidentMessage(row)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, domain.ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	return message, nil
}

func (r *sqliteMessageRepo) getPredefinedMessageByID(ctx context.Context, id int64) (*domain.PredefinedMessage, error) {
	row := r.db.QueryRowContext(ctx, `
		SELECT id, code, COALESCE(protocol_code, 0), message_en, message_fil, message_bikol, message_type, emergency_type_id, is_active, created_at, updated_at
		FROM predefined_messages
		WHERE id = ?
	`, id)
	message, err := scanPredefinedMessage(row)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, domain.ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	return message, nil
}

type incidentMessageScanner interface {
	Scan(dest ...interface{}) error
}

func scanIncidentMessage(scanner incidentMessageScanner) (*domain.IncidentMessage, error) {
	var message domain.IncidentMessage
	var senderUserID sql.NullInt64
	var receiverUserID sql.NullInt64
	var predefinedMessageID sql.NullInt64
	var customMessage sql.NullString
	var receivedAt sql.NullString
	var predefinedCode sql.NullString
	var messageEN sql.NullString
	var senderName sql.NullString
	var receiverName sql.NullString
	var sentAt string

	if err := scanner.Scan(
		&message.ID,
		&message.IncidentID,
		&senderUserID,
		&receiverUserID,
		&predefinedMessageID,
		&customMessage,
		&message.Channel,
		&sentAt,
		&receivedAt,
		&predefinedCode,
		&messageEN,
		&senderName,
		&receiverName,
	); err != nil {
		return nil, err
	}

	if senderUserID.Valid {
		message.SenderUserID = &senderUserID.Int64
	}
	if receiverUserID.Valid {
		message.ReceiverUserID = &receiverUserID.Int64
	}
	if predefinedMessageID.Valid {
		message.PredefinedMessageID = &predefinedMessageID.Int64
	}
	if customMessage.Valid {
		message.CustomMessage = customMessage.String
	}
	message.SentAt, _ = time.Parse(time.RFC3339Nano, sentAt)
	if receivedAt.Valid {
		parsed, _ := time.Parse(time.RFC3339Nano, receivedAt.String)
		message.ReceivedAt = &parsed
	}
	if predefinedCode.Valid {
		message.PredefinedCode = predefinedCode.String
	}
	if messageEN.Valid {
		message.MessageEN = messageEN.String
	}
	if senderName.Valid {
		message.SenderName = senderName.String
	}
	if receiverName.Valid {
		message.ReceiverName = receiverName.String
	}

	return &message, nil
}

type predefinedMessageScanner interface {
	Scan(dest ...interface{}) error
}

func scanPredefinedMessage(scanner predefinedMessageScanner) (*domain.PredefinedMessage, error) {
	var message domain.PredefinedMessage
	var messageFil sql.NullString
	var messageBikol sql.NullString
	var emergencyTypeID sql.NullInt64
	var isActive int
	var createdAt string
	var updatedAt string

	if err := scanner.Scan(
		&message.ID,
		&message.Code,
		&message.ProtocolCode,
		&message.MessageEN,
		&messageFil,
		&messageBikol,
		&message.MessageType,
		&emergencyTypeID,
		&isActive,
		&createdAt,
		&updatedAt,
	); err != nil {
		return nil, err
	}

	if messageFil.Valid {
		message.MessageFil = messageFil.String
	}
	if messageBikol.Valid {
		message.MessageBikol = messageBikol.String
	}
	if emergencyTypeID.Valid {
		message.EmergencyTypeID = &emergencyTypeID.Int64
	}
	message.IsActive = isActive == 1
	message.CreatedAt, _ = time.Parse(time.RFC3339Nano, createdAt)
	message.UpdatedAt, _ = time.Parse(time.RFC3339Nano, updatedAt)

	return &message, nil
}

func boolToInt(value bool) int {
	if value {
		return 1
	}
	return 0
}

func nullableString(value string) interface{} {
	if strings.TrimSpace(value) == "" {
		return nil
	}
	return value
}

func nullableTimeString(value *time.Time) interface{} {
	if value == nil {
		return nil
	}
	return value.UTC().Format(time.RFC3339Nano)
}
