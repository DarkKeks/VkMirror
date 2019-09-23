package ru.darkkeks.vkmirror.autoreg;

public class VkMirrorBot {

    public static final int VK_NOT_ASSIGNED = -1;

    private int id;
    private String username;
    private String token;
    private int vkId;

    public VkMirrorBot(String username, String token, int vkId) {
        this.username = username;
        this.token = token;
        this.vkId = vkId;
    }

    public VkMirrorBot(int id, String username, String token, int vkId) {
        this.id = id;
        this.username = username;
        this.token = token;
        this.vkId = vkId;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getToken() {
        return token;
    }

    public int getVkId() {
        return vkId;
    }

    @Override
    public String toString() {
        return "VkMirrorBot{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", token='" + token + '\'' +
                ", vkId=" + vkId +
                '}';
    }
}
