# Adding pets

Two pets can walk along the app row: one in the left half, one in the right. Neither ships with the repo. Pick any pet from [openpets.dev](https://openpets.dev), fit it with the script below, and tell the launcher which animations to use.

## Rights

The OpenPets app is MIT licensed, but the pets are not. Its terms say pets are user-submitted, community-made or fan-made, and that rights stay with their owners. Many are fan art of characters someone else owns. Keep pets on your own TV; `src/main/res/drawable-nodpi/` is in `.gitignore` so they never get committed.

## The sheet

A downloaded pet is a zip with `pet.json` and `spritesheet.webp`: 8 columns by 9 rows of 192x208 px frames. Rows usually mean:

| Row | Usually |
|---|---|
| 0 | Idle |
| 1 | Walk right |
| 2 | Walk left |
| 3 | Wave |
| 4 | Jump |
| 5 | Failed (sad, crying) |
| 6 | Waiting |
| 7 | Busy (running in place, typing) |
| 8 | Review (looking, thinking) |

Artists do not always follow this. Open the sheet and look before you trust a row. Things we hit:

- **Frames at different heights.** The pet floats and jitters. `tools/fit_pet.py` fixes it.
- **Both walk rows facing the same way.** One pet walked right while facing left. Set `flipRight` so the walk is mirrored.
- **A sleep row that wakes up again.** Cinder lies down by frame 4 and stands back up. Set `napHold` to the frame to stop on.
- **Odd frames.** One cat's last jump frame turned away from the camera. Lower that row's count in `frames` to skip it.

## Steps

1. Download a pet from openpets.dev and unzip it.
2. Fit the sheet:

   ```bash
   pip install pillow
   python3 tools/fit_pet.py ~/Downloads/<pet>/spritesheet.webp src/main/res/drawable-nodpi/pet_left.webp
   ```

   Use `pet_right.webp` for the right half. The script prints how many frames each row has.
3. Edit `LEFT_PET` or `RIGHT_PET` near the end of `MainActivity.kt`:

   | Field | What to set |
   |---|---|
   | `frames` | The counts the script printed, row 0 to 8. |
   | `walkLeft`, `walkRight` | The rows to play while walking each way. |
   | `flipRight` | `true` if the walk-right row faces left. |
   | `actions` | Rows to pick from while standing still. Leave out any that look wrong. |
   | `napRow` | A row played slowly for 9 seconds, or `-1` for none. |
   | `napHold` | The frame to stop on in `napRow`, or `-1` to loop it. |

4. Build and install as in the main README.

Without a file, that half of the row stays empty.

## Cost

Each pet redraws about 15 times a second while Home is on screen and stops when another app is open. Two pets used 5 to 12% CPU and about 20 MB of memory on the reference TV. The 768x936 size from `fit_pet.py` keeps the memory down; full-size sheets use about four times as much.
