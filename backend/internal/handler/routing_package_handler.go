package handler

import (
	"crypto/sha256"
	"encoding/hex"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"sync"
	"time"

	"github.com/travelingsalesman/agap/backend/internal/platform/response"
)

type RoutingPackageHandler struct {
	path string

	mu          sync.Mutex
	cachedSize  int64
	cachedMtime time.Time
	cachedSHA   string
}

type RoutingPackageMetadata struct {
	Available  bool   `json:"available"`
	FileName   string `json:"file_name,omitempty"`
	SizeBytes  int64  `json:"size_bytes,omitempty"`
	SHA256     string `json:"sha256,omitempty"`
	ModifiedAt string `json:"modified_at,omitempty"`
}

func NewRoutingPackageHandler(path string) *RoutingPackageHandler {
	return &RoutingPackageHandler{path: path}
}

func (h *RoutingPackageHandler) Metadata(w http.ResponseWriter, r *http.Request) {
	info, sha, err := h.packageInfo()
	if err != nil {
		if os.IsNotExist(err) {
			response.JSONResponse(w, http.StatusOK, RoutingPackageMetadata{Available: false})
			return
		}
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, RoutingPackageMetadata{
		Available:  true,
		FileName:   filepath.Base(h.path),
		SizeBytes:  info.Size(),
		SHA256:     sha,
		ModifiedAt: info.ModTime().UTC().Format(time.RFC3339Nano),
	})
}

func (h *RoutingPackageHandler) Download(w http.ResponseWriter, r *http.Request) {
	info, sha, err := h.packageInfo()
	if err != nil {
		if os.IsNotExist(err) {
			response.JSONError(w, http.StatusNotFound, "routing graph package is not prepared on this server")
			return
		}
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	w.Header().Set("Content-Type", "application/vnd.sqlite3")
	w.Header().Set("Content-Disposition", `attachment; filename="deployment-routing.db"`)
	w.Header().Set("ETag", `"`+sha+`"`)
	w.Header().Set("Cache-Control", "private, max-age=3600")
	http.ServeFile(w, r, h.path)
	_ = info
}

func (h *RoutingPackageHandler) packageInfo() (os.FileInfo, string, error) {
	info, err := os.Stat(h.path)
	if err != nil {
		return nil, "", err
	}
	if !info.Mode().IsRegular() {
		return nil, "", os.ErrNotExist
	}

	h.mu.Lock()
	defer h.mu.Unlock()

	if h.cachedSHA != "" && h.cachedSize == info.Size() && h.cachedMtime.Equal(info.ModTime()) {
		return info, h.cachedSHA, nil
	}

	file, err := os.Open(h.path)
	if err != nil {
		return nil, "", err
	}
	defer file.Close()

	hash := sha256.New()
	if _, err := io.Copy(hash, file); err != nil {
		return nil, "", err
	}

	h.cachedSize = info.Size()
	h.cachedMtime = info.ModTime()
	h.cachedSHA = hex.EncodeToString(hash.Sum(nil))
	return info, h.cachedSHA, nil
}
