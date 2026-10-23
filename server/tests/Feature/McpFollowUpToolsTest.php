<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Carbon;
use Laravel\Passport\Passport;
use phpseclib4\Crypt\RSA;
use Tests\TestCase;

/**
 * Tool follow-up di MCP Habitflow (tahap 35): log aktivitas (DONE) dan rencana besok (ACTIVE + picked_date)
 * ditulis sebagai follow-up, supaya muncul di halaman Kerja seperti jurnal kerja.
 */
class McpFollowUpToolsTest extends TestCase
{
    use RefreshDatabase;

    private const ACCEPT = ['Accept' => 'application/json, text/event-stream'];

    /** @var array{0: string, 1: string}|null */
    private static ?array $keys = null;

    private User $user;

    protected function setUp(): void
    {
        parent::setUp();

        if (! self::$keys) {
            $private = RSA::createKey(2048);
            self::$keys = [$private->toString('PKCS8'), $private->getPublicKey()->toString('PKCS8')];
        }
        config(['passport.private_key' => self::$keys[0], 'passport.public_key' => self::$keys[1]]);

        $this->user = User::create(['google_sub' => 'sub-1', 'email' => 'a@example.com', 'name' => 'A']);
        Passport::actingAs($this->user, ['mcp:use']);
    }

    /** Panggil tool lalu kembalikan hasil JSON-nya (teks pertama) dan status galat. */
    private function tool(string $name, array $arguments): array
    {
        $response = $this->postJson('/mcp', [
            'jsonrpc' => '2.0', 'id' => 1, 'method' => 'tools/call', 'params' => ['name' => $name, 'arguments' => $arguments],
        ], self::ACCEPT)->assertOk();

        $text = (string) $response->json('result.content.0.text');

        return [json_decode($text, true) ?? $text, (bool) $response->json('result.isError')];
    }

    public function test_tool_follow_up_terdaftar(): void
    {
        $names = collect($this->postJson('/mcp', ['jsonrpc' => '2.0', 'id' => 1, 'method' => 'tools/list'], self::ACCEPT)
            ->assertOk()->json('result.tools'))->pluck('name')->all();

        foreach (['list-follow-ups', 'create-follow-up', 'update-follow-up'] as $name) {
            $this->assertContains($name, $names);
        }
    }

    public function test_membuat_log_selesai_dan_rencana_besok(): void
    {
        [$done] = $this->tool('create-follow-up', ['title' => 'Skrip grant dijalankan', 'status' => 'DONE', 'done_on' => '2026-10-23']);
        [$plan] = $this->tool('create-follow-up', ['title' => 'Uji menu di MGTbpn', 'picked_date' => '2026-10-24', 'note' => 'cek login ulang']);

        $this->assertTrue($done['created']);
        $this->assertSame('DONE', $done['follow_up']['status']);
        $this->assertTrue($plan['created']);
        $this->assertSame('ACTIVE', $plan['follow_up']['status']);
        $this->assertSame('2026-10-24', $plan['follow_up']['picked_date']);

        $item = $this->user->followUps()->where('title', 'Skrip grant dijalankan')->first();
        $this->assertSame('DONE', $item->status);
        $this->assertSame('2026-10-23', $item->done_at->copy()->setTimezone('Asia/Jakarta')->toDateString());
        $this->assertNull($this->user->followUps()->where('title', 'Uji menu di MGTbpn')->value('done_at'));
    }

    public function test_tanggal_selesai_mengikuti_zona_waktu_pengguna(): void
    {
        $this->user->prayerDays()->create(['date' => '2026-10-23', 'timezone' => 'Asia/Makassar']);

        $this->tool('create-follow-up', ['title' => 'Kerja malam', 'status' => 'DONE', 'done_on' => '2026-10-23']);

        $doneAt = $this->user->followUps()->value('done_at');
        $this->assertSame('2026-10-23', Carbon::parse($doneAt)->setTimezone('Asia/Makassar')->toDateString());
    }

    public function test_menjalankan_ulang_tidak_membuat_follow_up_dobel(): void
    {
        $this->tool('create-follow-up', ['title' => 'Skrip grant dijalankan', 'status' => 'DONE', 'done_on' => '2026-10-23']);
        [$again] = $this->tool('create-follow-up', ['title' => 'skrip GRANT dijalankan', 'status' => 'DONE', 'done_on' => '2026-10-23']);
        $this->tool('create-follow-up', ['title' => 'Uji menu', 'picked_date' => '2026-10-24']);
        [$planAgain] = $this->tool('create-follow-up', ['title' => 'Uji menu', 'picked_date' => '2026-10-24']);

        $this->assertFalse($again['created']);
        $this->assertFalse($planAgain['created']);
        $this->assertSame(2, $this->user->followUps()->count());
    }

    public function test_judul_sama_di_tanggal_selesai_berbeda_tetap_dibuat(): void
    {
        $this->tool('create-follow-up', ['title' => 'Cek email', 'status' => 'DONE', 'done_on' => '2026-10-22']);
        [$next] = $this->tool('create-follow-up', ['title' => 'Cek email', 'status' => 'DONE', 'done_on' => '2026-10-23']);

        $this->assertTrue($next['created']);
        $this->assertSame(2, $this->user->followUps()->count());
    }

    public function test_daftar_menampilkan_yang_terbuka_dan_filter_selesai_per_tanggal(): void
    {
        $this->tool('create-follow-up', ['title' => 'Terbuka']);
        $this->tool('create-follow-up', ['title' => 'Selesai kemarin', 'status' => 'DONE', 'done_on' => '2026-10-23']);
        $this->tool('create-follow-up', ['title' => 'Selesai hari ini', 'status' => 'DONE', 'done_on' => '2026-10-24']);

        [$open] = $this->tool('list-follow-ups', []);
        [$doneKemarin] = $this->tool('list-follow-ups', ['done_on' => '2026-10-23']);

        $this->assertSame(['Terbuka'], array_column($open['follow_ups'], 'title'));
        $this->assertSame(['Selesai kemarin'], array_column($doneKemarin['follow_ups'], 'title'));
    }

    public function test_mengubah_menandai_selesai_membuka_lagi_dan_mengosongkan_field(): void
    {
        [$created] = $this->tool('create-follow-up', ['title' => 'Uji menu', 'picked_date' => '2026-10-24', 'note' => 'lama']);
        $id = $created['follow_up']['id'];

        [$done] = $this->tool('update-follow-up', ['id' => $id, 'status' => 'DONE']);
        $this->assertSame('DONE', $done['follow_up']['status']);
        $this->assertNotNull($done['follow_up']['done_at']);
        $this->assertSame('lama', $done['follow_up']['note']);

        [$reopened] = $this->tool('update-follow-up', ['id' => $id, 'status' => 'ACTIVE', 'note' => null]);
        $this->assertNull($reopened['follow_up']['done_at']);
        $this->assertNull($reopened['follow_up']['note']);
        $this->assertSame('2026-10-24', $reopened['follow_up']['picked_date']);
    }

    public function test_mengubah_selain_status_tidak_menyentuh_done_at(): void
    {
        [$created] = $this->tool('create-follow-up', ['title' => 'Selesai', 'status' => 'DONE', 'done_on' => '2026-10-23']);
        $before = $this->user->followUps()->value('done_at');

        $this->tool('update-follow-up', ['id' => $created['follow_up']['id'], 'note' => 'tambahan']);

        $this->assertEquals($before, $this->user->followUps()->value('done_at'));
    }

    public function test_tidak_bisa_mengubah_follow_up_milik_orang_lain(): void
    {
        $other = User::create(['google_sub' => 'sub-2', 'email' => 'b@example.com', 'name' => 'B']);
        $theirs = $other->followUps()->create(['title' => 'Milik B', 'status' => 'ACTIVE']);

        [$text, $isError] = $this->tool('update-follow-up', ['id' => $theirs->id, 'status' => 'DONE']);

        $this->assertTrue($isError);
        $this->assertSame('ACTIVE', $theirs->fresh()->status);
        [$list] = $this->tool('list-follow-ups', []);
        $this->assertSame(0, $list['count']);
    }

    public function test_masukan_divalidasi(): void
    {
        [, $badStatus] = $this->tool('create-follow-up', ['title' => 'x', 'status' => 'SELESAI']);
        [, $badDate] = $this->tool('create-follow-up', ['title' => 'x', 'picked_date' => '24-10-2026']);
        [, $noTitle] = $this->tool('create-follow-up', ['status' => 'DONE']);

        $this->assertTrue($badStatus);
        $this->assertTrue($badDate);
        $this->assertTrue($noTitle);
        $this->assertSame(0, $this->user->followUps()->count());
    }
}
