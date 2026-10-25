#pragma once
#include <string>
#include <chrono>

// Dasar semua modul client. Hook ke game memanggil onTick/onRender.
class Module {
public:
    explicit Module(std::string id) : id_(std::move(id)) {}
    virtual ~Module() = default;
    const std::string& id() const { return id_; }
    bool enabled = false;
    virtual void onTick() {}
    virtual void onRender(float /*dt*/) {}
    virtual void onInput(int /*key*/, bool /*down*/) {}
private:
    std::string id_;
};
