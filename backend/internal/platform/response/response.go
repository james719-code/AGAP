package response

import (
	"encoding/json"
	"net/http"
	"reflect"
)

type ErrorResponse struct {
	Error string `json:"error"`
}

func JSONResponse(w http.ResponseWriter, statusCode int, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(statusCode)
	if data != nil {
		data = sanitizeJSONData(data)
		_ = json.NewEncoder(w).Encode(data)
	}
}

func JSONError(w http.ResponseWriter, statusCode int, message string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(statusCode)
	_ = json.NewEncoder(w).Encode(ErrorResponse{Error: message})
}

func sanitizeJSONData(data interface{}) interface{} {
	if data == nil {
		return []interface{}{}
	}
	v := reflect.ValueOf(data)
	switch v.Kind() {
	case reflect.Slice:
		if v.IsNil() {
			return []interface{}{}
		}
	case reflect.Map:
		if v.IsNil() {
			return data
		}
		if m, ok := data.(map[string]interface{}); ok {
			for k, val := range m {
				if val == nil {
					continue
				}
				rv := reflect.ValueOf(val)
				if rv.Kind() == reflect.Slice && rv.IsNil() {
					m[k] = []interface{}{}
				}
			}
			return m
		}
	}
	return data
}

