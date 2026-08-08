package com.qwertimer.forge.data.db

import com.qwertimer.forge.domain.model.BodyRegion
import com.qwertimer.forge.domain.model.ExerciseCategory
import com.qwertimer.forge.domain.model.ExerciseCategory.BODYWEIGHT
import com.qwertimer.forge.domain.model.ExerciseCategory.CARDIO
import com.qwertimer.forge.domain.model.ExerciseCategory.MOBILITY
import com.qwertimer.forge.domain.model.ExerciseCategory.PLYOMETRIC

/**
 * The seed movement library.
 *
 * Everything here needs no equipment beyond a floor, a wall, and a sturdy chair or table, so a
 * session is never blocked on a gym being open. `impact = 2` marks the jumping movements, which
 * are filtered out entirely when high-impact work is turned off in settings.
 */
object ExerciseLibrary {

    private fun ex(
        id: String,
        name: String,
        category: ExerciseCategory,
        region: BodyRegion,
        minLevel: Int = 1,
        impact: Int = 0,
        timeBased: Boolean = false,
        reps: Int = 10,
        seconds: Int = 0,
        cue: String,
    ) = ExerciseEntity(
        id = id,
        name = name,
        category = category,
        region = region,
        minLevel = minLevel,
        impact = impact,
        timeBased = timeBased,
        defaultReps = reps,
        defaultSeconds = seconds,
        cue = cue,
    )

    val all: List<ExerciseEntity> = listOf(
        // ---- Bodyweight: upper push ----
        ex("pushup", "Push-up", BODYWEIGHT, BodyRegion.UPPER_PUSH, reps = 12,
            cue = "Hands under shoulders, ribs down, lower until elbows pass 90°."),
        ex("incline_pushup", "Incline push-up", BODYWEIGHT, BodyRegion.UPPER_PUSH, reps = 14,
            cue = "Hands on a bench or table. Easier the higher your hands."),
        ex("diamond_pushup", "Diamond push-up", BODYWEIGHT, BodyRegion.UPPER_PUSH, minLevel = 2, reps = 10,
            cue = "Thumbs and index fingers touching. Elbows stay close to the ribs."),
        ex("pike_pushup", "Pike push-up", BODYWEIGHT, BodyRegion.UPPER_PUSH, minLevel = 2, reps = 8,
            cue = "Hips high, crown of the head towards the floor between your hands."),
        ex("decline_pushup", "Decline push-up", BODYWEIGHT, BodyRegion.UPPER_PUSH, minLevel = 3, reps = 10,
            cue = "Feet elevated on a chair. Keep the hips from sagging."),
        ex("chair_dip", "Chair dip", BODYWEIGHT, BodyRegion.UPPER_PUSH, reps = 12,
            cue = "Shoulders down and back. Stop before the shoulders roll forward."),
        ex("wall_handstand_hold", "Wall handstand hold", BODYWEIGHT, BodyRegion.UPPER_PUSH,
            minLevel = 3, timeBased = true, seconds = 30,
            cue = "Chest to wall, ribs tucked, push the floor away."),

        // ---- Bodyweight: upper pull ----
        ex("table_row", "Table row", BODYWEIGHT, BodyRegion.UPPER_PULL, reps = 10,
            cue = "Lie under a sturdy table, pull your chest to the edge, squeeze the shoulder blades."),
        ex("towel_door_row", "Towel door row", BODYWEIGHT, BodyRegion.UPPER_PULL, reps = 12,
            cue = "Towel around a door handle, lean back, pull with the back rather than the arms."),
        ex("superman", "Superman", BODYWEIGHT, BodyRegion.UPPER_PULL, timeBased = true, seconds = 30,
            cue = "Lift chest and thighs off the floor. Look at the floor, not forwards."),
        ex("reverse_snow_angel", "Reverse snow angel", BODYWEIGHT, BodyRegion.UPPER_PULL, reps = 12,
            cue = "Face down, arms sweep from hips to overhead without touching the floor."),
        ex("chinup", "Chin-up", BODYWEIGHT, BodyRegion.UPPER_PULL, minLevel = 3, reps = 6,
            cue = "If you have a bar: full hang to chin over. Otherwise swap for a table row."),
        ex("prone_yts", "Prone Y-T-W", BODYWEIGHT, BodyRegion.UPPER_PULL, reps = 8,
            cue = "Face down, trace a Y, then a T, then a W with the arms. Thumbs up throughout."),

        // ---- Bodyweight: lower ----
        ex("air_squat", "Bodyweight squat", BODYWEIGHT, BodyRegion.LOWER, reps = 15,
            cue = "Feet shoulder width, sit between the heels, knees track over the toes."),
        ex("reverse_lunge", "Reverse lunge", BODYWEIGHT, BodyRegion.LOWER, reps = 10,
            cue = "Step back, back knee towards the floor, drive through the front heel."),
        ex("split_squat", "Split squat", BODYWEIGHT, BodyRegion.LOWER, reps = 10,
            cue = "Feet stay planted. Vertical torso, back knee grazes the floor."),
        ex("bulgarian_split_squat", "Bulgarian split squat", BODYWEIGHT, BodyRegion.LOWER, minLevel = 2, reps = 8,
            cue = "Rear foot on a chair. Most of the load on the front leg."),
        ex("glute_bridge", "Glute bridge", BODYWEIGHT, BodyRegion.LOWER, reps = 15,
            cue = "Heels close to the hips, squeeze the glutes at the top, ribs stay down."),
        ex("single_leg_bridge", "Single-leg glute bridge", BODYWEIGHT, BodyRegion.LOWER, minLevel = 2, reps = 10,
            cue = "One foot planted, the other knee hugged in. Keep the hips level."),
        ex("wall_sit", "Wall sit", BODYWEIGHT, BodyRegion.LOWER, timeBased = true, seconds = 45,
            cue = "Thighs parallel, back flat to the wall, breathe."),
        ex("calf_raise", "Calf raise", BODYWEIGHT, BodyRegion.LOWER, reps = 20,
            cue = "Full range: heels below the step, then all the way up onto the toes."),
        ex("cossack_squat", "Cossack squat", BODYWEIGHT, BodyRegion.LOWER, minLevel = 2, reps = 8,
            cue = "Wide stance, sit into one hip while the other leg straightens."),
        ex("single_leg_rdl", "Single-leg RDL", BODYWEIGHT, BodyRegion.LOWER, minLevel = 2, reps = 10,
            cue = "Hinge at the hip, back leg and torso form one line. Slow is the point."),

        // ---- Bodyweight: core ----
        ex("plank", "Plank", BODYWEIGHT, BodyRegion.CORE, timeBased = true, seconds = 45,
            cue = "Elbows under shoulders, squeeze glutes, do not let the hips drift up."),
        ex("side_plank", "Side plank", BODYWEIGHT, BodyRegion.CORE, timeBased = true, seconds = 30,
            cue = "Stack the shoulders and hips, push the floor away, hold both sides."),
        ex("hollow_hold", "Hollow hold", BODYWEIGHT, BodyRegion.CORE, minLevel = 2, timeBased = true, seconds = 30,
            cue = "Lower back pinned to the floor. Lower the arms and legs only as far as that allows."),
        ex("dead_bug", "Dead bug", BODYWEIGHT, BodyRegion.CORE, reps = 12,
            cue = "Opposite arm and leg extend slowly. The lower back never leaves the floor."),
        ex("bicycle_crunch", "Bicycle crunch", BODYWEIGHT, BodyRegion.CORE, reps = 16,
            cue = "Rotate through the ribs, not the neck. Slow beats fast."),
        ex("leg_raise", "Lying leg raise", BODYWEIGHT, BodyRegion.CORE, reps = 12,
            cue = "Hands under the hips, lower the legs only as far as you can stay flat."),
        ex("bird_dog", "Bird dog", BODYWEIGHT, BodyRegion.CORE, reps = 12,
            cue = "Opposite arm and leg, pause for a beat, keep the hips square."),
        ex("v_up", "V-up", BODYWEIGHT, BodyRegion.CORE, minLevel = 3, reps = 12,
            cue = "Reach hands to feet, fold at the hips, control the way down."),

        // ---- Bodyweight: full body ----
        ex("inchworm", "Inchworm", BODYWEIGHT, BodyRegion.FULL_BODY, reps = 8,
            cue = "Hinge, walk the hands out to a plank, walk them back."),
        ex("bear_crawl", "Bear crawl", BODYWEIGHT, BodyRegion.FULL_BODY, timeBased = true, seconds = 40,
            cue = "Knees an inch off the floor, hips low, opposite hand and foot."),
        ex("squat_thrust", "Squat thrust", BODYWEIGHT, BodyRegion.FULL_BODY, reps = 12,
            cue = "Hands down, feet back to a plank, feet in, stand. No jump."),
        ex("turkish_getup_bw", "Bodyweight get-up", BODYWEIGHT, BodyRegion.FULL_BODY, minLevel = 2, reps = 6,
            cue = "Floor to standing without using your hands, then reverse it."),

        // ---- Plyometrics ----
        ex("jump_squat", "Jump squat", PLYOMETRIC, BodyRegion.LOWER, minLevel = 2, impact = 2, reps = 10,
            cue = "Squat, explode up, land soft with the knees bent."),
        ex("tuck_jump", "Tuck jump", PLYOMETRIC, BodyRegion.LOWER, minLevel = 3, impact = 2, reps = 8,
            cue = "Drive the knees to the chest. Land quietly — noise means you are crashing."),
        ex("split_jump", "Split jump", PLYOMETRIC, BodyRegion.LOWER, minLevel = 2, impact = 2, reps = 10,
            cue = "Lunge, jump, switch legs mid-air, absorb the landing."),
        ex("broad_jump", "Broad jump", PLYOMETRIC, BodyRegion.LOWER, minLevel = 2, impact = 2, reps = 6,
            cue = "Swing the arms, jump forward for distance, stick the landing for a beat."),
        ex("skater_bound", "Skater bound", PLYOMETRIC, BodyRegion.LOWER, impact = 2, reps = 12,
            cue = "Bound side to side, land on one leg, control the wobble before the next."),
        ex("box_step_jump", "Step-up jump", PLYOMETRIC, BodyRegion.LOWER, impact = 2, reps = 10,
            cue = "Drive off one leg onto a low step. Step down, never jump down."),
        ex("plyo_pushup", "Plyo push-up", PLYOMETRIC, BodyRegion.UPPER_PUSH, minLevel = 3, impact = 1, reps = 8,
            cue = "Push hard enough for the hands to leave the floor. Elbows soft on landing."),
        ex("clap_pushup_incline", "Incline hand-release push-up", PLYOMETRIC, BodyRegion.UPPER_PUSH,
            minLevel = 2, impact = 1, reps = 10,
            cue = "Hands on a bench, push explosively so they leave the surface."),
        ex("pogo_hop", "Pogo hop", PLYOMETRIC, BodyRegion.LOWER, impact = 2, timeBased = true, seconds = 30,
            cue = "Stiff ankles, minimal knee bend, bounce off the balls of the feet."),
        ex("burpee", "Burpee", PLYOMETRIC, BodyRegion.FULL_BODY, minLevel = 2, impact = 2, reps = 10,
            cue = "Chest to floor, feet in, jump. Pace it — this is the one that ends people."),
        ex("star_jump", "Star jump", PLYOMETRIC, BodyRegion.FULL_BODY, impact = 2, reps = 12,
            cue = "Squat small, explode into a star shape, land soft."),

        // ---- Cardio ----
        ex("jumping_jack", "Jumping jacks", CARDIO, BodyRegion.FULL_BODY, impact = 2, timeBased = true, seconds = 45,
            cue = "Steady rhythm, full arm sweep overhead."),
        ex("seal_jack", "Seal jack", CARDIO, BodyRegion.FULL_BODY, impact = 2, timeBased = true, seconds = 40,
            cue = "Arms clap in front at shoulder height instead of overhead."),
        ex("high_knees", "High knees", CARDIO, BodyRegion.LOWER, impact = 2, timeBased = true, seconds = 40,
            cue = "Knees to hip height, stay on the balls of the feet, quick turnover."),
        ex("butt_kicks", "Butt kicks", CARDIO, BodyRegion.LOWER, impact = 2, timeBased = true, seconds = 40,
            cue = "Heels to glutes, tall posture, fast feet."),
        ex("mountain_climber", "Mountain climbers", CARDIO, BodyRegion.CORE, impact = 1, timeBased = true, seconds = 40,
            cue = "Plank position, hips low, drive the knees without bouncing."),
        ex("shadow_boxing", "Shadow boxing", CARDIO, BodyRegion.FULL_BODY, impact = 0, timeBased = true, seconds = 60,
            cue = "Light on the feet, hands up, throw combinations rather than single punches."),
        ex("fast_feet", "Fast feet", CARDIO, BodyRegion.LOWER, impact = 1, timeBased = true, seconds = 30,
            cue = "Tiny quick steps, athletic stance, as fast as you can hold form."),
        ex("skipping", "Skipping", CARDIO, BodyRegion.FULL_BODY, impact = 2, timeBased = true, seconds = 60,
            cue = "Rope or no rope. Small bounces, wrists do the work."),
        ex("stair_climb", "Stair climb", CARDIO, BodyRegion.LOWER, impact = 1, timeBased = true, seconds = 60,
            cue = "Drive through the whole foot. Take them one at a time, quickly."),
        ex("march_in_place", "March in place", CARDIO, BodyRegion.FULL_BODY, impact = 0, timeBased = true, seconds = 60,
            cue = "Low-impact option: knees to hip height, pump the arms."),
        ex("jog_in_place", "Jog in place", CARDIO, BodyRegion.FULL_BODY, impact = 1, timeBased = true, seconds = 60,
            cue = "Easy pace, land midfoot, keep the shoulders loose."),

        // ---- Mobility ----
        ex("worlds_greatest", "World's greatest stretch", MOBILITY, BodyRegion.FULL_BODY, timeBased = true, seconds = 45,
            cue = "Deep lunge, elbow to instep, then rotate and reach for the ceiling."),
        ex("cat_cow", "Cat-cow", MOBILITY, BodyRegion.CORE, timeBased = true, seconds = 40,
            cue = "Move one vertebra at a time, breathe with the movement."),
        ex("hip_circles", "Hip circles", MOBILITY, BodyRegion.LOWER, timeBased = true, seconds = 30,
            cue = "Hands on hips, big slow circles both directions."),
        ex("arm_circles", "Arm circles", MOBILITY, BodyRegion.UPPER_PUSH, timeBased = true, seconds = 30,
            cue = "Small to large, forwards then backwards."),
        ex("leg_swings", "Leg swings", MOBILITY, BodyRegion.LOWER, timeBased = true, seconds = 40,
            cue = "Hold something. Front to back, then side to side. Relaxed, not forced."),
        ex("thoracic_rotation", "Thoracic rotation", MOBILITY, BodyRegion.UPPER_PULL, timeBased = true, seconds = 40,
            cue = "Side lying, top arm opens to the floor behind you. Follow the hand with your eyes."),
        ex("down_dog_cobra", "Down dog to cobra", MOBILITY, BodyRegion.FULL_BODY, timeBased = true, seconds = 45,
            cue = "Flow between the two. Push the floor away in down dog, open the chest in cobra."),
        ex("hip_90_90", "90/90 hip switch", MOBILITY, BodyRegion.LOWER, timeBased = true, seconds = 45,
            cue = "Sit tall, rotate the knees floor to floor without using the hands."),
        ex("ankle_rocker", "Ankle rocker", MOBILITY, BodyRegion.LOWER, timeBased = true, seconds = 30,
            cue = "Half-kneeling, drive the knee past the toes with the heel down."),
        ex("couch_stretch", "Couch stretch", MOBILITY, BodyRegion.LOWER, timeBased = true, seconds = 45,
            cue = "Rear foot up a wall, squeeze the glute, stay tall. Both sides."),
        ex("childs_pose", "Child's pose", MOBILITY, BodyRegion.CORE, timeBased = true, seconds = 45,
            cue = "Knees wide, hips to heels, reach long and breathe into the ribs."),
        ex("towel_dislocates", "Towel shoulder pass-through", MOBILITY, BodyRegion.UPPER_PULL, timeBased = true, seconds = 40,
            cue = "Wide grip on a towel, pass overhead and behind. Widen the grip if it pinches."),
    )
}
